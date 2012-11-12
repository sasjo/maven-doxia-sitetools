package org.apache.maven.doxia.docrenderer.pdf.fo;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.apache.fop.apps.FopFactory;
import org.apache.maven.doxia.docrenderer.DocumentRendererContext;
import org.apache.maven.doxia.docrenderer.DocumentRendererException;
import org.apache.maven.doxia.docrenderer.pdf.AbstractPdfRenderer;
import org.apache.maven.doxia.docrenderer.pdf.PdfRenderer;
import org.apache.maven.doxia.document.DocumentModel;
import org.apache.maven.doxia.document.DocumentTOC;
import org.apache.maven.doxia.document.DocumentTOCItem;
import org.apache.maven.doxia.index.IndexEntry;
import org.apache.maven.doxia.index.IndexingSink;
import org.apache.maven.doxia.module.fo.FoAggregateSink;
import org.apache.maven.doxia.module.fo.FoSink;
import org.apache.maven.doxia.module.fo.FoSinkFactory;
import org.apache.maven.doxia.module.fo.FoUtils;
import org.apache.maven.doxia.module.site.SiteModule;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.util.HtmlTools;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.xml.sax.SAXParseException;

/**
 * PDF renderer that uses Doxia's FO module.
 * 
 * @author ltheussl
 * @version $Id$
 * @since 1.1
 */
@Component( role = PdfRenderer.class, hint = "fo" )
public class FoPdfRenderer
    extends AbstractPdfRenderer
{

    /*
     * Render has been extended with support for generated TOC based on the files referred in the TOC section of the
     * DocumentModel. /Samuel Sjoberg, 12-nov-2012.
     */

    /**
     * Thread local section counter. Used to generated a TOC with enumerated (unique) sections across multiple
     * documents.
     * 
     * @author Samuel Sjoberg
     */
    private static class SectionCounter
        extends ThreadLocal<Integer>
    {
        @Override
        protected Integer initialValue()
        {
            return 0;
        }

        public Integer getAndIncrement()
        {
            Integer value = get();
            set( value + 1 );
            return value;
        }
    }

    private SectionCounter sectionCounter = new SectionCounter();

    /**
     * {@inheritDoc}
     * 
     * @see org.apache.maven.doxia.module.fo.FoUtils#convertFO2PDF(File, File, String)
     */
    public void generatePdf( File inputFile, File pdfFile )
        throws DocumentRendererException
    {
        // Should take care of the document model for the metadata...
        generatePdf( inputFile, pdfFile, null );
    }

    /** {@inheritDoc} */
    @Override
    public void render( Map<String, SiteModule> filesToProcess, File outputDirectory, DocumentModel documentModel )
        throws DocumentRendererException, IOException
    {
        render( filesToProcess, outputDirectory, documentModel, null );
    }

    /** {@inheritDoc} */
    @Override
    public void render( Map<String, SiteModule> filesToProcess, File outputDirectory, DocumentModel documentModel,
                        DocumentRendererContext context )
        throws DocumentRendererException, IOException
    {
        // copy resources, images, etc.
        copyResources( outputDirectory );

        if ( documentModel == null )
        {
            getLogger().debug( "No document model, generating all documents individually." );

            renderIndividual( filesToProcess, outputDirectory, context );
            return;
        }

        String outputName = getOutputName( documentModel );

        File outputFOFile = new File( outputDirectory, outputName + ".fo" );
        if ( !outputFOFile.getParentFile().exists() )
        {
            outputFOFile.getParentFile().mkdirs();
        }

        File pdfOutputFile = new File( outputDirectory, outputName + ".pdf" );
        if ( !pdfOutputFile.getParentFile().exists() )
        {
            pdfOutputFile.getParentFile().mkdirs();
        }

        Writer writer = null;
        try
        {
            writer = WriterFactory.newXmlWriter( outputFOFile );

            // Using FoSectionAnchorSink for generated TOC support.
            FoAggregateSink sink = new FoSectionAnchorSink( writer );

            File fOConfigFile = new File( outputDirectory, "pdf-config.xml" );

            if ( fOConfigFile.exists() )
            {
                sink.load( fOConfigFile );
                getLogger().debug( "Loaded pdf config file: " + fOConfigFile.getAbsolutePath() );
            }

            String generateTOC =
                ( context != null && context.get( "generateTOC" ) != null ? context.get( "generateTOC" ).toString().trim()
                                : "start" );
            int tocPosition = 0;
            if ( "start".equalsIgnoreCase( generateTOC ) )
            {
                tocPosition = FoAggregateSink.TOC_START;
            }
            else if ( "end".equalsIgnoreCase( generateTOC ) )
            {
                tocPosition = FoAggregateSink.TOC_END;
            }
            else
            {
                tocPosition = FoAggregateSink.TOC_NONE;
            }

            final DocumentTOC documentToc = documentModel.getToc();

            // Replace configured TOC with a true, multi-level TOC.
            if ( !isTocDescriptorMissing( documentToc ) )
            {
                // Generated TOC support.
                documentModel.setToc( buildTocFromSources( documentToc, context ) );
            }

            sink.setDocumentModel( documentModel, tocPosition );

            sink.beginDocument();

            sink.coverPage();

            if ( tocPosition == FoAggregateSink.TOC_START )
            {
                sink.toc();
            }

            if ( isTocDescriptorMissing( documentToc ) )
            {
                getLogger().info( "No TOC is defined in the document descriptor. Merging all documents." );

                mergeAllSources( filesToProcess, sink, context );
            }
            else
            {
                getLogger().debug( "Using TOC defined in the document descriptor." );

                mergeSourcesFromTOC( documentToc, sink, context );
            }

            if ( tocPosition == FoAggregateSink.TOC_END )
            {
                sink.toc();
            }

            sink.endDocument();
        }
        finally
        {
            IOUtil.close( writer );
            sectionCounter.remove();
        }

        generatePdf( outputFOFile, pdfOutputFile, documentModel );
    }

    private boolean isTocDescriptorMissing( DocumentTOC documentToc )
    {
        return ( documentToc == null ) || ( documentToc.getItems() == null );
    }

    /**
     * Generate a table of contents from the source documents. This method uses an {@link IndexingSink} to build a
     * proper TOC model based on the included documents. Each document will act as a chapter holding its content in the
     * same way as the standard TOCs.
     * <p>
     * Added for TOC support.
     * 
     * @param documentToc the original document TOC
     * @param context the renderer context
     * @return the generated TOC
     * @throws IOException if failing to parse modules
     * @throws DocumentRendererException if failing to parse modules
     */
    private DocumentTOC buildTocFromSources( DocumentTOC documentToc, DocumentRendererContext context )
        throws IOException, DocumentRendererException
    {

        DocumentTOC toc = new DocumentTOC();
        toc.setName( documentToc.getName() );

        List<IndexEntry> chapters = new ArrayList<IndexEntry>();
        for ( DocumentTOCItem tocItem : documentToc.getItems() )
        {
            if ( tocItem.getRef() == null )
            {
                continue;
            }

            // Use an IndexSink to capture sub-sections.
            IndexEntry chapter = new IndexEntry( tocItem.getRef() );
            chapter.setTitle( tocItem.getName() );
            IndexingSink tocSink = new IndexingSink( chapter );

            String href = getDocumentTocItemHref( tocItem );
            renderModules( href, tocSink, tocItem, context );
            if ( tocItem.getItems() != null )
            {
                parseTocItems( tocItem.getItems(), tocSink, context );
            }
            chapters.add( chapter );
        }
        for ( IndexEntry entry : chapters )
        {
            toc.addItem( createTOCItem( entry, 0 ) );
        }

        return toc;
    }

    /**
     * Create a TOC entry from an {@link IndexEntry}.
     * <p>
     * Added for TOC support.
     * 
     * @param entry the index entry
     * @param depth the TOC depth
     * @return the TOC entry.
     */
    private DocumentTOCItem createTOCItem( IndexEntry entry, int depth )
    {
        DocumentTOCItem item = new DocumentTOCItem();
        item.setName( HtmlTools.escapeHTML( entry.getTitle() ) );
        if ( depth == 0 )
        {
            item.setRef( entry.getId() );
        }
        else
        {
            item.setRef( "#" + HtmlTools.encodeId( "section-" + sectionCounter.getAndIncrement() ) );
        }
        for ( IndexEntry child : entry.getChildEntries() )
        {
            item.addItem( createTOCItem( child, depth + 1 ) );
        }
        return item;
    }

    /** {@inheritDoc} */
    @Override
    public void renderIndividual( Map<String, SiteModule> filesToProcess, File outputDirectory )
        throws DocumentRendererException, IOException
    {
        renderIndividual( filesToProcess, outputDirectory, null );
    }

    /** {@inheritDoc} */
    @Override
    public void renderIndividual( Map<String, SiteModule> filesToProcess, File outputDirectory,
                                  DocumentRendererContext context )
        throws DocumentRendererException, IOException
    {
        for ( Map.Entry<String, SiteModule> entry : filesToProcess.entrySet() )
        {
            String key = entry.getKey();
            SiteModule module = entry.getValue();

            File fullDoc = new File( getBaseDir(), module.getSourceDirectory() + File.separator + key );

            String output = key;
            String lowerCaseExtension = module.getExtension().toLowerCase( Locale.ENGLISH );
            if ( output.toLowerCase( Locale.ENGLISH ).indexOf( "." + lowerCaseExtension ) != -1 )
            {
                output = output.substring( 0, output.toLowerCase( Locale.ENGLISH ).indexOf( "." + lowerCaseExtension ) );
            }

            File outputFOFile = new File( outputDirectory, output + ".fo" );
            if ( !outputFOFile.getParentFile().exists() )
            {
                outputFOFile.getParentFile().mkdirs();
            }

            File pdfOutputFile = new File( outputDirectory, output + ".pdf" );
            if ( !pdfOutputFile.getParentFile().exists() )
            {
                pdfOutputFile.getParentFile().mkdirs();
            }

            FoSink sink =
                (FoSink) new FoSinkFactory().createSink( outputFOFile.getParentFile(), outputFOFile.getName() );
            sink.beginDocument();
            parse( fullDoc.getAbsolutePath(), module.getParserId(), sink, context );
            sink.endDocument();

            generatePdf( outputFOFile, pdfOutputFile, null );
        }
    }

    private void mergeAllSources( Map<String, SiteModule> filesToProcess, FoAggregateSink sink,
                                  DocumentRendererContext context )
        throws DocumentRendererException, IOException
    {
        for ( Map.Entry<String, SiteModule> entry : filesToProcess.entrySet() )
        {
            String key = entry.getKey();
            SiteModule module = entry.getValue();
            sink.setDocumentName( key );
            File fullDoc = new File( getBaseDir(), module.getSourceDirectory() + File.separator + key );

            parse( fullDoc.getAbsolutePath(), module.getParserId(), sink, context );
        }
    }

    private void mergeSourcesFromTOC( DocumentTOC toc, Sink sink, DocumentRendererContext context )
        throws IOException, DocumentRendererException
    {
        parseTocItems( toc.getItems(), sink, context );
    }

    private void parseTocItems( List<DocumentTOCItem> items, Sink sink, DocumentRendererContext context )
        throws IOException, DocumentRendererException
    {
        for ( DocumentTOCItem tocItem : items )
        {
            if ( tocItem.getRef() == null )
            {
                if ( getLogger().isInfoEnabled() )
                {
                    getLogger().info( "No ref defined for tocItem " + tocItem.getName() );
                }

                continue;
            }

            String href = getDocumentTocItemHref( tocItem );
            renderModules( href, sink, tocItem, context );

            if ( tocItem.getItems() != null )
            {
                parseTocItems( tocItem.getItems(), sink, context );
            }
        }
    }

    private String getDocumentTocItemHref( DocumentTOCItem tocItem )
    {
        String href = StringUtils.replace( tocItem.getRef(), "\\", "/" );
        if ( href.lastIndexOf( '.' ) != -1 )
        {
            href = href.substring( 0, href.lastIndexOf( '.' ) );
        }
        return href;
    }

    private void renderModules( String href, Sink sink, DocumentTOCItem tocItem, DocumentRendererContext context )
        throws DocumentRendererException, IOException
    {
        Collection<SiteModule> modules = siteModuleManager.getSiteModules();
        for ( SiteModule module : modules )
        {
            File moduleBasedir = new File( getBaseDir(), module.getSourceDirectory() );

            if ( moduleBasedir.exists() )
            {
                String doc = href + "." + module.getExtension();
                File source = new File( moduleBasedir, doc );

                // Velocity file?
                if ( !source.exists() )
                {
                    if ( href.indexOf( "." + module.getExtension() ) != -1 )
                    {
                        doc = href + ".vm";
                    }
                    else
                    {
                        doc = href + "." + module.getExtension() + ".vm";
                    }
                    source = new File( moduleBasedir, doc );
                }

                if ( source.exists() )
                {
                    if ( sink instanceof FoAggregateSink )
                    {
                        FoAggregateSink foSink = (FoAggregateSink) sink;
                        foSink.setDocumentName( doc );
                        foSink.setDocumentTitle( tocItem.getName() );
                    }

                    parse( source.getPath(), module.getParserId(), sink, context );
                }
            }
        }
    }

    /**
     * @param inputFile
     * @param pdfFile
     * @param documentModel could be null
     * @throws DocumentRendererException if any
     * @since 1.1.1
     */
    private void generatePdf( File inputFile, File pdfFile, DocumentModel documentModel )
        throws DocumentRendererException
    {
        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug( "Generating: " + pdfFile );
        }

        try
        {
            configureFop();
            FoUtils.convertFO2PDF( inputFile, pdfFile, null, documentModel );
        }
        catch ( TransformerException e )
        {
            if ( ( e.getCause() != null ) && ( e.getCause() instanceof SAXParseException ) )
            {
                SAXParseException sax = (SAXParseException) e.getCause();

                StringBuilder sb = new StringBuilder();
                sb.append( "Error creating PDF from " ).append( inputFile.getAbsolutePath() ).append( ":" ).append( sax.getLineNumber() ).append( ":" ).append( sax.getColumnNumber() ).append( "\n" );
                sb.append( e.getMessage() );

                throw new DocumentRendererException( sb.toString() );
            }

            throw new DocumentRendererException( "Error creating PDF from " + inputFile + ": " + e.getMessage() );
        }
    }

    /**
     * Optionally configure the FOP factory with a user config file.
     * 
     * @throws DocumentRendererException if failing to load the configuration
     */
    private void configureFop()
        throws DocumentRendererException
    {
        try
        {
            URL url = getClass().getResource( "/fop-userconfig.xml" );
            if ( url != null )
            {
                getLogger().debug( "Using FOP user config: " + url.toExternalForm() );

                Field field = FoUtils.class.getDeclaredField( "FOP_FACTORY" );
                field.setAccessible( true );
                FopFactory fopFactory = (FopFactory) field.get( null );
                fopFactory.setUserConfig( url.toExternalForm() );
            }
        }
        catch ( Exception e )
        {
            throw new DocumentRendererException( "Failed to configured FOP_FACTORY.", e );
        }
    }
}

package org.apache.maven.doxia.siterenderer.sink;

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

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.text.html.HTML.Attribute;

import org.apache.maven.doxia.module.xhtml.XhtmlSink;
import org.apache.maven.doxia.sink.render.RenderingContext;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.util.HtmlTools;

import org.codehaus.plexus.util.StringUtils;

/**
 * Sink for site rendering.
 *
 * @author <a href="mailto:evenisse@codehaus.org">Emmanuel Venisse</a>
 * @version $Id$
 */
public class SiteRendererSink
    extends XhtmlSink
    implements Sink, org.codehaus.doxia.sink.Sink
{
    private String date = "";

    private String title = "";

    private List<String> authors = new ArrayList<String>();

    private final StringWriter headWriter;

    private StringBuffer sectionTitleBuffer;

    private boolean sectionHasID;

    private boolean isSectionTitle;

    private Set<String> anchorsInSectionTitle;

    private final Writer writer;

    private RenderingContext renderingContext;

    /**
     * Construct a new SiteRendererSink.
     *
     * @param renderingContext the RenderingContext.
     */
    public SiteRendererSink( RenderingContext renderingContext )
    {
        this( new StringWriter(), renderingContext );
    }

    /**
     * Construct a new SiteRendererSink.
     *
     * @param writer the writer for the sink.
     * @param renderingContext the RenderingContext.
     */
    private SiteRendererSink( StringWriter writer, RenderingContext renderingContext )
    {
        super( writer );

        this.writer = writer;
        this.headWriter = new StringWriter();
        this.renderingContext = renderingContext;
    }

    /** {@inheritDoc} */
    @Override
    public void title_()
    {
        if ( getTextBuffer().length() > 0 )
        {
            title = getTextBuffer().toString();
        }

        resetTextBuffer();
    }

    /**
     * {@inheritDoc}
     *
     * Do nothing.
     * @see org.apache.maven.doxia.module.xhtml.XhtmlSink#title()
     */
    @Override
    public void title()
    {
        // nop
    }

    /**
     * <p>Getter for the field <code>title</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getTitle()
    {
        return title;
    }

    /** {@inheritDoc} */
    @Override
    public void author_()
    {
        if ( getTextBuffer().length() > 0 )
        {
            String text = HtmlTools.escapeHTML( getTextBuffer().toString() );
            text = StringUtils.replace( text, "&amp;#", "&#" );
            authors.add( text.trim() );
        }

        resetTextBuffer();
    }

    /**
     * <p>Getter for the field <code>authors</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<String> getAuthors()
    {
        return authors;
    }

    /** {@inheritDoc} */
    @Override
    public void date_()
    {
        if ( getTextBuffer().length() > 0 )
        {
            date = getTextBuffer().toString().trim();
        }

        resetTextBuffer();
    }

    /**
     * <p>Getter for the field <code>date</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDate()
    {
        return date;
    }

    /**
     * {@inheritDoc}
     *
     * Do nothing.
     * @see org.apache.maven.doxia.module.xhtml.XhtmlSink#body_()
     */
    @Override
    public void body_()
    {
        // nop
    }

    /**
     * {@inheritDoc}
     *
     * Do nothing.
     * @see org.apache.maven.doxia.module.xhtml.XhtmlSink#body()
     */
    @Override
    public void body()
    {
        // nop
    }

    /**
     * <p>getBody.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getBody()
    {
        return writer.toString();
    }

    /**
     * <p>getHead.</p>
     *
     * @return a {@link java.lang.String} object.
     *
     * @since 1.1.1
     */
    public String getHead()
    {
        return headWriter.toString();
    }

    /** {@inheritDoc} */
    @Override
    public void head_()
    {
        setHeadFlag( false );
    }

    /** {@inheritDoc} */
    @Override
    public void head()
    {
        setHeadFlag( true );
    }

    /** {@inheritDoc} */
    @Override
    public void anchor( String name, SinkEventAttributes attributes )
    {
        super.anchor( name, attributes );
        if ( isSectionTitle )
        {
            if ( anchorsInSectionTitle == null )
            {
                anchorsInSectionTitle = new HashSet<String>();
            }
            anchorsInSectionTitle.add( name );
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onSectionTitle( int depth, SinkEventAttributes attributes )
    {
        this.sectionTitleBuffer = new StringBuffer();
        sectionHasID = ( attributes != null && attributes.isDefined ( Attribute.ID.toString() ) );
        isSectionTitle = true;

        super.onSectionTitle( depth, attributes );
    }

    /** {@inheritDoc} */
    @Override
    protected void onSectionTitle_( int depth )
    {
        String sectionTitle = sectionTitleBuffer.toString();
        this.sectionTitleBuffer = null;

        if ( !sectionHasID && !StringUtils.isEmpty( sectionTitle ) )
        {
            String id = HtmlTools.encodeId( sectionTitle );
            if ( ( anchorsInSectionTitle == null ) || (! anchorsInSectionTitle.contains( id ) ) )
            {
                anchor( id );
                anchor_();
            }
        }
        else
        {
            sectionHasID = false;
        }

        this.isSectionTitle = false;
        anchorsInSectionTitle = null;
        super.onSectionTitle_( depth );
    }

    /**
     * <p>Getter for the field <code>renderingContext</code>.</p>
     *
     * @return the current rendering context
     * @since 1.1
     */
    public RenderingContext getRenderingContext()
    {
        return renderingContext;
    }

    /** {@inheritDoc} */
    @Override
    public void text( String text )
    {
        if ( sectionTitleBuffer != null )
        {
            // this implies we're inside a section title, collect text events for anchor generation
            sectionTitleBuffer.append( text );
        }

        super.text( text );
    }

    /** {@inheritDoc} */
    @Override
    protected void write( String text )
    {
        String txt = text;

        if ( isHeadFlag() )
        {
            headWriter.write( unifyEOLs( txt ) );

            return;
        }

        if ( renderingContext != null )
        {
            String relativePathToBasedir = renderingContext.getRelativePath();

            if ( relativePathToBasedir == null )
            {
                txt = StringUtils.replace( txt, "$relativePath", "." );
            }
            else
            {
                txt = StringUtils.replace( txt, "$relativePath", relativePathToBasedir );
            }
        }

        super.write( txt );
    }
}

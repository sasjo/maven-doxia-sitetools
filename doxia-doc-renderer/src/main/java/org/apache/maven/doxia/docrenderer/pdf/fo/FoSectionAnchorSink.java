package org.apache.maven.doxia.docrenderer.pdf.fo;

import java.io.Writer;
import java.util.List;
import java.util.Stack;

import javax.swing.text.MutableAttributeSet;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.doxia.document.DocumentModel;
import org.apache.maven.doxia.document.DocumentTOC;
import org.apache.maven.doxia.document.DocumentTOCItem;
import org.apache.maven.doxia.module.fo.FoAggregateSink;
import org.apache.maven.doxia.module.fo.NumberedListItem;
import org.apache.maven.doxia.sink.SinkEventAttributes;

/**
 * Extension of {@link FoAggregateSink} that ensures all section titles have a valid section reference. This allows them
 * to be referenced from the TOC.
 * 
 * @author Samuel Sjoberg
 */
public class FoSectionAnchorSink
    extends FoHighlighterSink
{

    /** The document model to be used by this sink. */
    private DocumentModel docModel;

    /** Current position of the TOC, see {@link #TOC_POSITION} */
    private int tocPosition;

    /** Used to get the current position in the TOC. */
    private final Stack<NumberedListItem> tocStack = new Stack<NumberedListItem>();

    /** Track when a title is renderer. */
    private boolean inTitle;

    /** Track when an anchor is rendered. */
    private boolean inAnchor;

    /** Counter for generated section anchors. */
    private int sectionCounter = 0;

    public FoSectionAnchorSink( Writer writer )
    {
        super( writer );
    }

    @Override
    public void setDocumentModel( DocumentModel model, int tocPos )
    {
        DocumentTOC toc = model.getToc();
        super.setDocumentModel( model, tocPos );

        // Reverse the stupidity with including TOC in the TOC.
        toc.getItems().remove( 0 );
        this.docModel = model;
        this.tocPosition = tocPos;

    }

    @Override
    public void anchor( String name, SinkEventAttributes attributes )
    {
        if ( inTitle )
        {
            return;
        }
        inAnchor = true;
        super.anchor( name, attributes );
    }

    @Override
    public void anchor_()
    {
        if ( inTitle )
        {
            return;
        }
        super.anchor_();
        inAnchor = false;
    }

    @Override
    public void sectionTitle( int level, SinkEventAttributes attributes )
    {
        super.sectionTitle( level, attributes );
        inTitle = true;
    }

    @Override
    public void sectionTitle_( int level )
    {
        inTitle = false;
        super.sectionTitle_( level );
    }

    @Override
    public void text( String text, SinkEventAttributes attributes )
    {
        if ( inTitle && !inAnchor )
        {
            String anchor = "./#section-" + ( sectionCounter++ );
            writeStartTag( INLINE_TAG, "id", anchor );
        }
        super.text( text, attributes );
        if ( inTitle && !inAnchor )
        {
            writeEndTag( INLINE_TAG );
        }
    }

    // Fixed toc number concatenation. Should file a defect...
    private String currentTocNumber()
    {
        String ch = ( tocStack.get( 0 ) ).getListItemSymbol();

        for ( int i = 1; i < tocStack.size(); i++ )
        {
            ch = ch + tocStack.get( i ).getListItemSymbol();
        }

        if ( !ch.endsWith( "." ) )
        {
            ch += ".";
        }

        return ch;
    }

    // ----------------------------------------------------------------------
    // Below are copy/paste methods from FoAggregateSink
    // Copied to support the currentTocNumber fix.
    // ----------------------------------------------------------------------

    /**
     * Writes a table of contents. The DocumentModel has to contain a DocumentTOC for this to work.
     */
    public void toc()
    {
        if ( docModel == null || docModel.getToc() == null || docModel.getToc().getItems() == null
            || this.tocPosition == TOC_NONE )
        {
            return;
        }

        DocumentTOC toc = docModel.getToc();

        writeln( "<fo:page-sequence master-reference=\"toc\" initial-page-number=\"1\" format=\"i\">" );
        regionBefore( toc.getName() );
        regionAfter( getFooterText() );
        writeStartTag( FLOW_TAG, "flow-name", "xsl-region-body" );
        writeStartTag( BLOCK_TAG, "id", "./toc" );
        chapterHeading( toc.getName(), false );
        writeln( "<fo:table table-layout=\"fixed\" width=\"100%\" >" );
        writeEmptyTag( TABLE_COLUMN_TAG, "column-width", "0.45in" );
        writeEmptyTag( TABLE_COLUMN_TAG, "column-width", "0.4in" );
        writeEmptyTag( TABLE_COLUMN_TAG, "column-width", "0.4in" );
        writeEmptyTag( TABLE_COLUMN_TAG, "column-width", "5in" ); // TODO
                                                                  // {$maxBodyWidth
                                                                  // - 1.25}in
        writeStartTag( TABLE_BODY_TAG );

        writeTocItems( toc.getItems(), 1 );

        writeEndTag( TABLE_BODY_TAG );
        writeEndTag( TABLE_TAG );
        writeEndTag( BLOCK_TAG );
        writeEndTag( FLOW_TAG );
        writeEndTag( PAGE_SEQUENCE_TAG );
    }

    private void writeTocItems( List<DocumentTOCItem> tocItems, int level )
    {
        final int maxTocLevel = 4;

        if ( level < 1 || level > maxTocLevel )
        {
            return;
        }

        tocStack.push( new NumberedListItem( NUMBERING_DECIMAL ) );

        for ( DocumentTOCItem tocItem : tocItems )
        {
            String ref = getIdName( tocItem.getRef() );

            writeStartTag( TABLE_ROW_TAG, "keep-with-next", "auto" );

            if ( level > 2 )
            {
                for ( int i = 0; i < level - 2; i++ )
                {
                    writeStartTag( TABLE_CELL_TAG );
                    writeSimpleTag( BLOCK_TAG );
                    writeEndTag( TABLE_CELL_TAG );
                }
            }

            writeStartTag( TABLE_CELL_TAG, "toc.cell" );
            writeStartTag( BLOCK_TAG, "toc.number.style" );

            NumberedListItem current = tocStack.peek();
            current.next();
            write( currentTocNumber() );

            writeEndTag( BLOCK_TAG );
            writeEndTag( TABLE_CELL_TAG );

            String span = "3";

            if ( level > 2 )
            {
                span = Integer.toString( 5 - level );
            }

            writeStartTag( TABLE_CELL_TAG, "number-columns-spanned", span, "toc.cell" );
            MutableAttributeSet atts = getFoConfiguration().getAttributeSet( "toc.h" + level + ".style" );
            atts.addAttribute( "text-align-last", "justify" );
            writeStartTag( BLOCK_TAG, atts );
            writeStartTag( BASIC_LINK_TAG, "internal-destination", ref );
            write( tocItem.getName() );
            writeEndTag( BASIC_LINK_TAG );
            writeEmptyTag( LEADER_TAG, "toc.leader.style" );
            writeStartTag( INLINE_TAG, "page.number" );
            writeEmptyTag( PAGE_NUMBER_CITATION_TAG, "ref-id", ref );
            writeEndTag( INLINE_TAG );
            writeEndTag( BLOCK_TAG );
            writeEndTag( TABLE_CELL_TAG );
            writeEndTag( TABLE_ROW_TAG );

            if ( tocItem.getItems() != null )
            {
                writeTocItems( tocItem.getItems(), level + 1 );
            }
        }

        tocStack.pop();
    }

    /**
     * Translates the given name to a usable id. Prepends "./" and strips any extension.
     * 
     * @param name the name for the current document.
     * @return String
     */
    private String getIdName( String name )
    {
        if ( StringUtils.isEmpty( name ) )
        {
            getLog().warn( "Empty document reference, links will not be resolved correctly!" );
            return "";
        }

        String idName = name.replace( '\\', '/' );

        // prepend "./" and strip extension
        if ( !idName.startsWith( "./" ) )
        {
            idName = "./" + idName;
        }

        if ( idName.substring( 2 ).lastIndexOf( "." ) != -1 )
        {
            idName = idName.substring( 0, idName.lastIndexOf( "." ) );
        }

        while ( idName.indexOf( "//" ) != -1 )
        {
            idName = StringUtils.replace( idName, "//", "/" );
        }

        return idName;
    }

}
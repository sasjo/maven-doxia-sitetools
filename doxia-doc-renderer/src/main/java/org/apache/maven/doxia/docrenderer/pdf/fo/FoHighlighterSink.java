package org.apache.maven.doxia.docrenderer.pdf.fo;

import java.io.Writer;
import java.lang.reflect.Method;

import javax.xml.transform.TransformerException;

import org.apache.maven.doxia.module.fo.FoAggregateSink;
import org.apache.maven.doxia.module.fo.FoSink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.util.HtmlTools;

/**
 * Special purpose {@link FoAggregateSink} that adds syntax highlighting to verbatim content. The syntax highlighting is
 * produced by {@link FoCodeHighlighter}.
 * 
 * @author Samuel Sjoberg
 */
public class FoHighlighterSink
    extends FoAggregateSink
{

    /** Track verbatim mode to active highlighter. */
    private boolean verbatim;

    /** Lightweight highlighter. */
    private FoCodeHighlighter highlighter;

    public FoHighlighterSink( Writer writer )
    {
        super( writer );
        try
        {
            highlighter = new FoCodeHighlighter();
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Failed to create FoCodeHighlighter.", e );
        }
    }

    @Override
    public void verbatim_()
    {
        this.verbatim = false;
        super.verbatim_();
    }

    @Override
    public void verbatim( SinkEventAttributes attributes )
    {
        this.verbatim = true;
        super.verbatim( attributes );
    }

    @Override
    protected void content( String text )
    {

        if ( verbatim )
        {
            write( highlight( text ) );
        }
        else
        {
            super.content( text );
        }
    }

    /**
     * Highlight the text using a {@link FoCodeHighlighter}.
     * 
     * @param text the text (code) to highlight
     * @return the highlighted text
     */
    private String highlight( final String text )
    {

        try
        {
            return highlighter.highlight( text );
        }
        catch ( TransformerException e )
        {
            System.err.println( "Failed to create syntax highlight. Falling back to plain text." );
            e.printStackTrace();
        }

        // We're skipping escaped here as the pdf-config.xml anyway must be modified to preserve line-breaks
        // and white-space. However, we still need to properly escape XML and handle symbols.

        try
        {
            // Let's use reflection to avoid pasting code. This is exception handling anyway...
            Method needsSymbolFont = FoSink.class.getDeclaredMethod( "needsSymbolFont" );
            needsSymbolFont.setAccessible( true );

            String escaped = HtmlTools.escapeHTML( text, true );

            int length = escaped.length();
            StringBuffer buffer = new StringBuffer( length );

            for ( int i = 0; i < length; ++i )
            {
                char c = escaped.charAt( i );
                if ( (Boolean) needsSymbolFont.invoke( null, c ) )
                {
                    buffer.append( "<fo:inline font-family=\"Symbol\">" ).append( c ).append( "</fo:inline>" );

                }
                else
                {
                    buffer.append( c );
                }
            }

            return buffer.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();

            // Last resort, let's use the default escaped method.
            return escaped( text, true );
        }
    }
}

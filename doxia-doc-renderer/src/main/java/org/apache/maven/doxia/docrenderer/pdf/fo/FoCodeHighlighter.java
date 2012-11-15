package org.apache.maven.doxia.docrenderer.pdf.fo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Thread safe code syntax highlighter for XSL-FO. Highlighting is performed using the XSLTHL library.
 * <p>
 * Language is detected in the following way:
 * <ol>
 * <li>If the first line contains e.g. <code>@lang css</code>, remove the first line and use <tt>css</tt> formatting
 * rules. It is valid to wrap the <code>@lang</code> tag inside comments, such as <code>//</code> or
 * <code>{@code<!--}</code> to allow for embedding in source files. This makes it easy to possible to combine syntax
 * highlighting with the snipped macro.</li>
 * <li>If no <code>@lang</code> hint exists, check if language is a markup language by looking for
 * <code>{@code</}</code>. If found, use <tt>xml</tt> formatting.</li>
 * <li>As last resort, default to <tt>java</tt>.
 * </ol>
 * 
 * @author Samuel Sjoberg
 */
public class FoCodeHighlighter
{

    /*
     * With a different xslt file, this highlighter could format code for other output formats (sinks) such as (X)HTML,
     * but this would also require changes to the sink's content method. /sasjo
     */

    static
    {
        // Path to the xslthl syntax highlighters configuration.
        System.setProperty( "xslthl.config",
                            FoCodeHighlighter.class.getResource( "/highlighters/xslthl-config.xml" ).toExternalForm() );
    }

    /** Lock for creating templates. */
    private static Lock lock = new ReentrantLock();

    /** XSLT templates. */
    private static Templates templates;

    /**
     * Convenience wrapper for a source code block.
     * 
     * @author Samuel Sjoberg, Extenda AB
     */
    private static class Code
    {

        /** Language detection pattern. */
        private static final Pattern LANG_PATTERN = Pattern.compile( "@lang (\\w+)" );

        /** Formatting language. */
        private String language;

        /** Source code. */
        private String source;

        private Code( String language, String source )
        {
            this.language = language;
            this.source = source;
        }

        public static Code valueOf( String source )
        {
            String language = null;
            int firstLine = source.indexOf( "\n" );
            if ( firstLine != -1 )
            {
                Matcher m = LANG_PATTERN.matcher( source.substring( 0, firstLine ) );
                if ( m.find() )
                {
                    language = m.group( 1 );
                    source = source.substring( firstLine + 1 );
                }
            }

            if ( language == null )
            {
                if ( source.contains( "</" ) )
                {
                    language = "xml";
                }
                else
                {
                    language = "java";
                }
            }

            return new Code( language, source );
        }

        /**
         * Returns an XML representation of the source code. This representation is suitable to pass to XSLTHL for
         * syntax highlighting.
         * 
         * @return the XML for syntax highlighting.
         */
        public String toXml()
        {
            String code;
            if ( source.contains( "<![CDATA[" ) )
            {
                // If source contains CDATA tags, we need to escape the end tag.
                code = source.replace( "]]>", "]]]]><![CDATA[>" );
            }
            else
            {
                code = source;
            }

            StringBuilder xml = new StringBuilder( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );
            xml.append( "<code language=\"" ).append( language ).append( "\">" );
            xml.append( "<![CDATA[" ).append( code ).append( "]]></code>" );
            return xml.toString();
        }

        @Override
        public String toString()
        {
            return toXml();
        }
    }

    /**
     * Create a new highlighter.
     * 
     * @throws IOException if failing to read the XSL stylesheet
     * @throws TransformerException if failing to create the XSL templates
     */
    public FoCodeHighlighter()
        throws IOException, TransformerException
    {
        if ( templates == null )
        {
            try
            {
                lock.lock();
                if ( templates == null )
                {
                    // Create templates only once.
                    init();
                }
            }
            finally
            {
                lock.unlock();
            }
        }

    }

    /**
     * Create the shared, cached {@link Templates}.
     * 
     * @throws IOException if failing to read the XSL stylesheet
     * @throws TransformerException if failing to create the XSL templates
     */
    private void init()
        throws IOException, TransformerException
    {
        URL stylesheet = FoCodeHighlighter.class.getResource( "xslthl-fo.xsl" );
        try
        {
            StreamSource source = new StreamSource( stylesheet.openStream(), stylesheet.toURI().toASCIIString() );
            templates = TransformerFactory.newInstance().newTemplates( source );
        }
        catch ( URISyntaxException e )
        {
            throw new IOException( String.format( "Failed to load stylesheet [%s].", stylesheet ), e );
        }
    }

    /**
     * Highlight the passed source code for XSL-FO.
     * <p>
     * Language is detected in the following way:
     * <ol>
     * <li>If the first line of <code>source</code> contains e.g. <code>@lang css</code>, remove the first line and use
     * <tt>css</tt> formatting rules. It is valid to wrap the <code>@lang</code> tag inside comments, such as
     * <code>//</code> or <code>{@code<!--}</code> to allow for embedding in source files. This makes it easy to
     * possible to combine syntax highlighting with the snipped macro.</li>
     * <li>If no <code>@lang</code> hint exists, check if language is a markup language by looking for
     * <code>{@code</}</code>. If found, use <tt>xml</tt> formatting.</li>
     * <li>As last resort, default to <tt>java</tt>.
     * </ol>
     * 
     * @param source the source code to highlight
     * @return the source code with FO-XSL formatting
     * @throws TransformerException if failing to create the highlights
     */
    public String highlight( String source )
        throws TransformerException
    {

        Code code = Code.valueOf( source );
        Source xmlSource = new StreamSource( new StringReader( code.toXml() ) );
        OutputStream transformResult = new ByteArrayOutputStream();

        Transformer transformer = templates.newTransformer();
        Result result = new StreamResult( transformResult );
        transformer.transform( xmlSource, result );

        return transformResult.toString();
    }
}

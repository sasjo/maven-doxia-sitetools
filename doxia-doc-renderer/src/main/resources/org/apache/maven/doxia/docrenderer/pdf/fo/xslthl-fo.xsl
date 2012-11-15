<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:s6hl="http://net.sf.xslthl/ConnectorSaxon6" 
    xmlns:sbhl="http://net.sf.xslthl/ConnectorSaxonB" 
    xmlns:xhl="http://net.sf.xslthl/ConnectorXalan" 
    xmlns:saxon6="http://icl.com/saxon" 
    xmlns:saxonb="http://saxon.sf.net/" 
    xmlns:xalan="http://xml.apache.org/xalan" 
    xmlns:xslthl="http://xslthl.sf.net" 
    extension-element-prefixes="s6hl sbhl xhl xslthl"
    exclude-result-prefixes="s6hl sbhl xhl xslthl xalan saxon6 saxonb">

    <xsl:output method="xml" encoding="utf8" omit-xml-declaration="yes" indent="no" />

    <!-- this parameter is used to set the location of the filename -->
    <xsl:param name="xslthl.config" />

    <!-- this construction is needed to have the saxon and xalan connectors working alongside each other -->
    <xalan:component prefix="xhl" functions="highlight">
        <xalan:script lang="javaclass" src="xalan://net.sf.xslthl.ConnectorXalan" />
    </xalan:component>

    <!-- for saxon 6 -->
    <saxon6:script implements-prefix="s6hl" language="java" src="java:net.sf.xslthl.ConnectorSaxon6" />

    <!-- for saxon 8.5 and later -->
    <saxonb:script implements-prefix="sbhl" language="java" src="java:net.sf.xslthl.ConnectorSaxonB" />

    <!-- start of the stylesheet -->
    <xsl:template match="/">
        <xsl:apply-templates />
    </xsl:template>

    <xsl:template match="code">
        <xsl:call-template name="syntax-highlight">
            <xsl:with-param name="language">
                <xsl:value-of select="@language" />
            </xsl:with-param>
            <xsl:with-param name="source" select="." />
        </xsl:call-template>
    </xsl:template>


    <!-- highlighting of the xslthl tags -->
    
    <!-- Based on configuration from DocBook XSL -->
    
    <xsl:template match="xslthl:*" mode="xslthl">
        <xsl:value-of select="." />  <!-- fallback -->
    </xsl:template>

    <xsl:template match='xslthl:keyword' mode="xslthl">
        <fo:inline font-weight="bold">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:string' mode="xslthl">
        <fo:inline font-weight="bold" font-style="italic">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:comment' mode="xslthl">
        <fo:inline font-style="italic">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:tag' mode="xslthl">
        <fo:inline font-weight="bold">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:attribute' mode="xslthl">
        <fo:inline font-weight="bold">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:value' mode="xslthl">
        <fo:inline font-weight="bold">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:number' mode="xslthl">
        <xsl:apply-templates mode="xslthl" />
    </xsl:template>

    <xsl:template match='xslthl:annotation' mode="xslthl">
        <fo:inline color="gray">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:directive' mode="xslthl">
        <xsl:apply-templates mode="xslthl" />
    </xsl:template>

    <!-- Not sure which element will be in final XSLTHL 2.0 -->
    <xsl:template match='xslthl:doccomment|xslthl:doctype' mode="xslthl">
        <fo:inline font-weight="bold">
            <xsl:apply-templates mode="xslthl" />
        </fo:inline>
    </xsl:template>

    <!-- This template will perform the actual highlighting -->
    <xsl:template name="syntax-highlight">
        <xsl:param name="language" />
        <xsl:param name="source" />
        <xsl:choose>
            <xsl:when test="function-available('s6hl:highlight')">
                <xsl:variable name="highlighted" select="s6hl:highlight($language, $source, $xslthl.config)" />
                <xsl:apply-templates select="$highlighted" mode="xslthl" />
            </xsl:when>
            <xsl:when test="function-available('sbhl:highlight')">
                <xsl:variable name="highlighted" select="sbhl:highlight($language, $source, $xslthl.config)" />
                <xsl:apply-templates select="$highlighted" mode="xslthl" />
            </xsl:when>
            <xsl:when test="function-available('xhl:highlight')">
                <xsl:variable name="highlighted" select="xhl:highlight($language, $source, $xslthl.config)" />
                <xsl:apply-templates select="$highlighted" mode="xslthl" />
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates select="$source/*|$source/text()" mode="xslthl" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
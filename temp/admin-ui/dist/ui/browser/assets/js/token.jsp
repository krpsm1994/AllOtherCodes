<?xml version="1.0" encoding="ISO-8859-1" ?>
<jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" version="2.0">
    <jsp:directive.page language="java"
        contentType="application/javascript" pageEncoding="ISO-8859-1" import="com.opentext.sap.damint.rest.*"/>
<jsp:text>var otdamToken = '</jsp:text><jsp:expression>session.getAttribute(RestApiConstants.OAUTH_TOKEN)</jsp:expression><jsp:text>';</jsp:text>
</jsp:root>
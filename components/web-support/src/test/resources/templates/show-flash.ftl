<#-- @ftlvariable name="other" type="java.lang.String" -->
<#-- @ftlvariable name="flash" type="java.util.Collection<java.lang.String>" -->
<#list flash as message>
    ${message}
</#list>
<#if other??>
    ${other}
</#if>

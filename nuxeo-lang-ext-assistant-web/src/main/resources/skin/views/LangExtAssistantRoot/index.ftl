<@extends src="base.ftl">
<@block name="header">You signed in as ${Context.principal}</@block>

<@block name="content">

<div style="margin: 10px 10px 10px 10px">
	
<table>
  <tr>
  <td width="30%">
    <p>Installed Message bundles:</p>
    <ul>
      <#list availableLanguages as lang>
        <li><a href="${This.path}/lang/${lang}">${lang.displayName}</a> (<a href="${This.path}/lang/${lang}/file">File</a>, <a href="${This.path}/lang/${lang}/diff">Diff</a>)</li>
      </#list>
    </ul>
  </td>
  <td>
  <div style="margin:10px;">
    <p>Hello and welcome to Nuxeo's translation assistant. This is currently a Beta version. It lacks certain features, you might find bugs, etc... So don't forget to save your work often by downloading the associated file. You'll be able to reload it using the file input below.</p>
    <p>The list of available languages is displayed on the left. Click on any of those to display a table containing the keys, original english labels and selected language labels.</p>
    <p>If you're looking for more details on the subject, try <a href="http://dev.blogs.nuxeo.com/2012/06/qa-friday-translating-nuxeo.html" >this blog post</a>.</p>
	<form action="${This.path}/upload" method="post" enctype="multipart/form-data">
	  <p>Upload a message file : <input type="file" name="uploadedFile" size="50" />	   <input type="submit" value="Upload It" />
        <#if error_message>
          <div><span class="errorMessage">${error_message}</span></div>
        </#if>
	  </p>
      <p>The selected file must be a valid messages_Lang.properties file. If the locale does not exist, it will be created. Newly create locale are not installed at the moment. This means you won't be able to use hot reload, download the file or the diff for them. To install a new locale, you need to modify the deployment-fragment.xml file in <a href="https://github.com/nuxeo/nuxeo-platform-lang-ext">nuxeo-platform-lang-ext</a>. Insert  &lt;supported-locale>LANG&lt;/supported-locale&gt; like the others:</p>
      <p>
<pre>
  &lt;extension target="faces-config#APPLICATION_LOCALE"&gt;
    &ltlocale-config&gt;
      &lt;supported-locale&gt;ar&lt;/supported-locale&gt;
      &lt;supported-locale&gt;ca&lt;/supported-locale&gt;
      &lt;supported-locale&gt;cn&lt;/supported-locale&gt;
      &lt;supported-locale&gt;de&lt;/supported-locale&gt;
      &lt;supported-locale&gt;el_GR&lt;/supported-locale&gt;
      &lt;supported-locale&gt;CUSTOM_LANG&lt;/supported-locale&gt;
      &lt;supported-locale&gt;eu&lt;/supported-locale&gt;
      &lt;supported-locale&gt;gl&lt;/supported-locale&gt;
      &lt;supported-locale&gt;it&lt;/supported-locale&gt;
      &lt;supported-locale&gt;sr&lt;/supported-locale&gt;
      &lt;supported-locale&gt;vn&lt;/supported-locale&gt;
    &lt;/locale-config&gt;
  &lt;/extension&gt;
</pre>
      </p>
    </div>
  </td>
  </tr>
</table>
</div>

</@block>
</@extends>
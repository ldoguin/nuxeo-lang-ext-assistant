<@extends src="base.ftl">
<@block name="header">You signed in as ${Context.principal}</@block>

<@block name="content">

<div style="margin: 10px 10px 10px 10px">

<form  method="post">
<div id="dlLinks">
<ul>
  <li><input id="emptyLabelFilter" type="checkbox" name="filter" value="emptyLabel" /> Show Empty Labels Only</li>
  <li><a href="${This.path}/lang/${languageKey}/file">Download File</a></li>
  <li><a href="${This.path}/lang/${languageKey}/diff">Download Git Diff</a></li>
  <li><a href="${This.path}/lang/${languageKey}/removeDuplicatedKeys">Remove Duplicates Keys</a></li>
  <li><a href="${This.path}">Back To List</a></li>
</ul>
</div>
<#if languageProperties>
<table style="width=100%" id="formTable">
    <tr class="rowStyle">
      <td style="padding-left:5px;padding-top:5px;"><h3>Key: <input id="keyFilterInput" type="text" name="keyFilter"/></h3></td><td style="padding-left:5px;padding-top:5px;" ><h3>Label en: <input id="labelFilterInput" type="text" name="labelFilter"/> </h3></td><td style="padding-left:5px;padding-top:5px;" ><h3>Label ${languageKey}: <input id="langFilterInput" type="text" name="langFilter"/> </h3></td>
    </tr>
<#list sortedKeys as prop>
  <tr class="keyLabel rowStyle" id="${prop}">
    <td class="key keyStyle" ><div style="padding-left:10px;width:30em;word-wrap: break-word;">${prop}</div></td>
    <td class="label labelStyle" style="padding-left:10px;">${defaultProperties[prop]?html}</td>
    <td class="lang langStyle"><textarea class="target" id="input_${prop}" name="${prop}">${languageProperties[prop]?html}</textarea></td>
  </tr>
</#list>
</table>
</#if>
</form >
</div>

<script src="http://api.jquery.com/scripts/events.js"></script>
<script>
function intersect(arr1, arr2) {
    var r = [], o = {}, l = arr2.length, i, v;
    for (i = 0; i < l; i++) {
        o[arr2[i]] = true;
    }
    l = arr1.length;
    for (i = 0; i < l; i++) {
        v = arr1[i];
        if (v in o) {
            r.push(v);
        }
    }
    return r;
}

var delay = (function(){
  var timer = 0;
  return function(callback, ms){
    clearTimeout (timer);
    timer = setTimeout(callback, ms);
  };
})();

var showEmptyLabelsOnly = false;
var hideValidateLabels = false;
var ev;
$(".target").keypress(function(event) {
  if ( event.which == 13 ) {
     event.preventDefault();
   }
  save(event.currentTarget.name);
});

function save(changedId) {
    var jsonObj = new Array();
    var field, fieldValue;
    field = changedId.replace(/(:|\.)/g,'\\$1');
    fieldValue = $('#input_'+field).val();
    jsonObj.push({id: changedId, value: fieldValue});
    data = JSON.stringify(jsonObj);
    jQuery.ajax({
        type: 'PUT',
        contentType: 'application/json',
        url: '${This.path}/lang/${languageKey}/update',
        dataType: "json",
        data: data,
        success: function(data, textStatus, jqXHR){
	   modifiedFields = new Array();
        },
        error: function(jqXHR, textStatus, errorThrown){
            alert('Update error: ' + textStatus);
        }
    });
}

var keyIdList;
function filterKeyId(event) {
delay(function(){
  if ( event.which == 13 ) {
     event.preventDefault();
   }
  keyIdList = new Array();
  key = $.trim($('#keyFilterInput').val());
  if (key != '') {
    parentTR = $("#formTable tr[id*='"+key+"']");
    for (var i = 0; i < parentTR.length; i++) {
        keyIdList.push(parentTR[i].id);
    }
  }
filter();
    }, 1000 );
}

var labelIdList;
function filterLabelId(event) {
delay(function(){
  if ( event.which == 13 ) {
     event.preventDefault();
   }
  labelIdList = new Array();
  label = $.trim($('#labelFilterInput').val());
  if (label != '') {
    parentTR = $("#formTable td.label:contains('"+label+"')").parent();
    for (var i = 0; i < parentTR.length; i++) {
        labelIdList.push(parentTR[i].id);
    }
  }
filter();
    }, 1000 );
}

var langIdList;
function filterLangId(event) {
delay(function(){
  if ( event.which == 13 ) {
     event.preventDefault();
   }
  langIdList = new Array();
  lang = $.trim($('#langFilterInput').val());
  if (lang != '') {
    parentTR = $("#formTable textarea:contains('"+lang+"')").parent().parent();
    for (var i = 0; i < parentTR.length; i++) {
        langIdList.push(parentTR[i].id);
    }
  }
filter();
    }, 1000 );
}

var emptyLangIdList;
function filterEmptyLangId() {
  emptyLangIdList = new Array();
  parentTR = $("textarea:empty").parent().parent();
  for (var i = 0; i < parentTR.length; i++) {
      emptyLangIdList.push(parentTR[i].id);
  }
  filter();
}

function filter() {
  var commonId = new Array();
  if (keyIdList && keyIdList.length > 0) {
    commonId = keyIdList;
    if (langIdList && langIdList.length > 0) {
      commonId = intersect(commonId, langIdList);
    }
    if (labelIdList && labelIdList.length > 0) {
      commonId = intersect(commonId, labelIdList);
    }
  } else if (labelIdList && labelIdList.length > 0) {
    commonId = labelIdList;
    if (langIdList && langIdList.length > 0) {
      commonId = intersect(commonId, langIdList);
    }
  } else if (langIdList && langIdList.length > 0) {
   commonId = langIdList;
  }

  if (showEmptyLabelsOnly) {
   commonId = intersect(commonId,emptyLangIdList)
  }

  if (commonId && commonId.length > 0) {
    $("#formTable tr.keyLabel").hide();
    for (var i = 0; i < commonId.length; i++) {
      id = commonId[i];
      id = id.replace(/\./g, '\\.')
      $("#"+ id).show();
    }
  } else {
    if (showEmptyLabelsOnly) {
      parentTR = $("textarea:empty").parent().parent().show();
      parentTR = $("textarea:parent").parent().parent().hide();
    } else {
      $("#formTable tr.keyLabel").show();
    }
  }
}

$('#keyFilterInput').keyup(filterKeyId);
$('#labelFilterInput').keyup(filterLabelId);
$('#langFilterInput').keyup(filterLangId);
$('#emptyLabelFilter').change(function () {
  showEmptyLabelsOnly = $(this).is(':checked');
if (showEmptyLabelsOnly) {
  filterEmptyLangId();
} else {
  emptyLangIdList = new Array();
  filterEmptyLangId();
}
});
</script>

</@block>
</@extends>
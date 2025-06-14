<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Copy Layers</title>
    <g:set var="baseUrl" value="${request.scheme}://${request.serverName}:${request.serverPort}${request.contextPath}" />
    <meta name="breadcrumbs" content="${g.createLink(uri: baseUrl)}, Spatial Service"/>

    <meta name="layout" content="ala-main"/>

    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>

<body class="fluid">
<div class="col-lg-8">
    <h1>Copy Layers from Remote Server</h1>
</div>

<g:set var="spatialConfig" bean="spatialConfig"/>

<g:if test="${spatialServiceUrl == localUrl}">
    <div class="col-lg-8">
        <div class="warning">The local and remote server is the same so layers cannot be copied</div>
    </div>
</g:if>

<div class=" col-lg-4">
    <div class="panel panel-default">
        <div class="panel-heading">
            <h4 class="panel-title">Navigation</h4>
        </div>

        <div class="panel-body">
            <li><g:link controller="manageLayers" action="uploads">Show all uploads</g:link></li>
            <li><g:link controller="manageLayers" action="layers">Show all Layers</g:link></li>
            <li><g:link controller="tasks" action="index">Show all Tasks</g:link></li>
            <li><g:link controller="manageLayers" action="remote">Copy Layers from remote server</g:link></li>
        </div>
    </div>
</div>

<g:if test="${error != null}">
    <b class="error">${error}</b>
    <br/>
    <br/>
</g:if>
<div class="container-fluid">
    This will copy a layer from a remote spatial-service to the local spatial-service.
    <br/>
    <h3>Config</h3>
    <table>
        <tbody>
        <tr>
            <td>URL of the remote spatial-service</td>
            <td><input type="text" id="spatialServiceUrl" value="${spatialServiceUrl}" style='width:500px;margin-left:20px;'/><button onclick="refreshLayerList()">Refresh layer list</button></td>
        </tr>
        <tr>
            <td>JWT for the remote service. It must have the role ${spatialConfig.layerCopyRole} and not expire before the task is finished.</td>
            <td><textarea id="jwt" style='margin-left:20px;' cols="100" rows="10"></textarea></td>
        </tr>
        </tbody>
    </table>
</div>
<div class="container-fluid">
    <br/>
    <br/>
    <h3>List of layers</h3>
    Layer filter
    <select id="listSelector">
        <option value="divLocal">local only</option>
        <option value="divRemote" selected>remote only</option>
        <option value="divBoth">local and remote</option>
    </select>
    <br/>
    <br/>

    <div id="divBoth" class="listBlock" style="display:none">
        <table class="table table-bordered" id="layersBothTable">
            <thead>
            <tr>
                <th>Date added</th>
                <th>FieldId</th>
                <th>LayerId</th>
                <th>Name</th>
                <th>Enabled (L)</th>
                <th>Enabled (R)</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <g:each var="item" in="${layersBoth}">
                <tr>
                    <td>${item.dt_added}</td>
                <td>${item.id}
                    <a target="_blank" href="${localUrl}/manageLayers/field/${item.id}">(L)</a>
                    <a target="_blank" href="${spatialServiceUrl}/manageLayers/field/${item.id}">(R)</a></td></td>
                <td>${item.layerId}
                    <a target="_blank" href="${localUrl}/manageLayers/layer/${item.layerId}">(L)</a>
                    <a target="_blank" href="${spatialServiceUrl}/manageLayers/layer/${item.layerId}">(R)</a></td>
                <td>${item.name}</td>
                <td id="txtenable${item.id}">${item?.local?.enabled}</td>
                <td>${item?.remote?.enabled}</td>
                <td><g:if test="${!item?.local?.enabled}"><button onclick="enable('${item.id}')"
                                                                  id="enable${item.id}">enable</button></g:if></td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </div>

    <div id="divRemote" class="listBlock">

        <table class="table table-bordered" id="layersRemoteOnlyTable">
            <thead>
            <tr>
                <th>Date added</th>
                <th>FieldId</th>
                <th>LayerId</th>
                <th>Name</th>
                <th>Enabled (L)</th>
                <th>Enabled (R)</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <g:each var="item" in="${layersRemoteOnly}">
                <tr>
                    <td>${item.dt_added}</td>
                    <td>${item.id}
                        <a target="_blank" href="${spatialServiceUrl}/manageLayers/field/${item.id}">(R)</a></td>
                    <td>${item.layerId}
                        <a target="_blank" href="${spatialServiceUrl}/manageLayers/layer/${item.layerId}">(R)</a></td>
                    <td>${item.name}</td>
                    <td>${item?.local?.enabled}</td>
                    <td>${item?.remote?.enabled}</td>
                    <td id="txtcopy${item.id}"><button onclick="copy('${item.id}')" id="copy${item.id}">copy</button>
                    </td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </div>

    <div id="divLocal" class="listBlock" style="display:none">

        <table class="table table-bordered" id="layersLocalOnlyTable">
            <thead>
            <tr>
                <th>Date added</th>
                <th>FieldId</th>
                <th>LayerId</th>
                <th>Name</th>
                <th>Enabled (L)</th>
                <th>Enabled (R)</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <g:each var="item" in="${layersLocalOnly}">
                <tr>
                    <td>${item.dt_added}</td>
                    <td>${item.id}
                        <a target="_blank" href="${localUrl}/manageLayers/field/${item.id}">(L)</a>
                    </td>
                    <td>${item.layerId}
                        <a target="_blank" href="${localUrl}/manageLayers/layer/${item.layerId}">(L)</a>
                    </td>
                    <td id="txtenable${item.id}">${item?.local?.enabled}</td>
                    <td>${item?.remote?.enabled}</td>
                    <td>${item.name}</td>
                    <td><<g:if test="${!item?.local?.enabled}"><button onclick="enable('${item.id}')"
                                                                       id="enable${item.id}">enable</button></g:if></td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </div>
</div>

<script>
    function confirmDelete(id, name) {
        if (confirm("Permanently delete layer " + name + "?")) {
            var url = "delete/" + id
            $(location).attr('href', url);
        }
    }

    jQuery(document).ready(function () {

        jQuery("div.dataTables_filter input").attr("placeholder", "Filter within results");

        $("#listSelector").change(function () {
            $(".listBlock").hide()
            $("#" + listSelector.value).show();
        });

        // setup the table
        jQuery('#layersRemoteOnlyTable').dataTable({
            "aaSorting": [
                [0, "desc"]
            ],
            "aLengthMenu": [
                [10, 25, 50, 100, -1],
                [10, 25, 50, 100, "All"]
            ],
            "sPaginationType": "full_numbers",
            "sDom": '<"sort-options"fl<"clear">>rt<"pagination"ip<"clear">>',
            "oLanguage": {
                "sSearch": ""
            }
        });

        jQuery('#layersBothTable').dataTable({
            "aaSorting": [
                [0, "desc"]
            ],
            "aLengthMenu": [
                [10, 25, 50, 100, -1],
                [10, 25, 50, 100, "All"]
            ],
            "sPaginationType": "full_numbers",
            "sDom": '<"sort-options"fl<"clear">>rt<"pagination"ip<"clear">>',
            "oLanguage": {
                "sSearch": ""
            }
        });

        jQuery('#layersLocalOnlyTable').dataTable({
            "aaSorting": [
                [0, "desc"]
            ],
            "aLengthMenu": [
                [10, 25, 50, 100, -1],
                [10, 25, 50, 100, "All"]
            ],
            "sPaginationType": "full_numbers",
            "sDom": '<"sort-options"fl<"clear">>rt<"pagination"ip<"clear">>',
            "oLanguage": {
                "sSearch": ""
            }
        });


    });

    function copy(id) {
        var jwt = document.getElementById('jwt').value;
        if (jwt == "") {
            alert("Please provide a JWT");
            return;
        }

        $.post("${localUrl}/manageLayers/copy?fieldId=" + id + "&spatialServiceUrl=" + encodeURIComponent("${spatialServiceUrl}") +
            "&jwt=" + encodeURIComponent(jwt));
        $('#copy' + id)[0].remove();
        $('#txtcopy' + id)[0].innerText = 'copying';
    }

    function enable(id) {
        $.post("${localUrl}/manageLayers/enable?id=" + id);
        $.post("${localUrl}/manageLayers/enable?id=" + id.substr(2));
        while ($('#enable' + id).length > 0) {
            $('#enable' + id)[0].remove();
            $('#txtenable' + id)[0].innerText = 'true';
        }
    }

    function downloadLayers(type) {
        var downloadurl = "/layers-service/layers";
        var query = jQuery("div.dataTables_filter input").val();
        if (type == "json") {
            downloadurl += ".json";
        } else {
            downloadurl += ".csv";
        }
        if (query != "") {
            downloadurl += "?q=" + query;
        }
        location.href = downloadurl;
    }

    function refreshLayerList() {
        const remoteUrl = document.getElementById('spatialServiceUrl').value;
        const url = new URL(window.location.href);
        url.searchParams.set('remoteUrl', remoteUrl);
        window.location.href = url.toString();
    }
</script>
</div>
</body>
</html>

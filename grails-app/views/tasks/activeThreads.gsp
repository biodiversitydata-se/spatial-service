
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="ala-main">
    <title>Active tasks</title>
    <meta name="breadcrumbs" content="${g.createLink(controller: 'main', action: 'index')}, Spatial Service"/>
    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>

<body class="fluid">

<div class="col-lg-8">
</div>

<div class="col-lg-4">
    <div class="panel panel-default">
        <div class="panel-heading">Navigation</div>

        <div class="panel-body">
            <li><g:link controller="manageLayers" action="uploads">Show all uploads</g:link></li>
            <li><g:link controller="manageLayers" action="layers">Show all Layers</g:link></li>
            <li><g:link controller="tasks" action="index">Show all Tasks</g:link></li>
            <li><g:link controller="tasks" action="activeThreads">Show active Tasks</g:link></li>
            <li><g:link controller="manageLayers" action="remote">Copy Layers from remote server</g:link></li>
        </div>
    </div>
</div>


<div id="list-task" class="content scaffold-list" role="main">
Check "active thread" column. If the column is empty, it indicates the thread running this task is completed or not started yet.
    <table class="table table-bordered table-striped" name="tasks">
        <thead>
            <th>Id</th>
            <th>Name</th>
            <th>Log</th>
            <th>Status</th>
            <th>Created</th>
            <th>Active thread</th>
            <th>Action</th>
        </thead>
        <tbody>
        <g:set var="status" value="${["Queued","Running","Cancelled","Error","Successful"]}"></g:set>
        <g:each in="${tasks}" status="i" var="task">
            <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
                <td><g:link action="show"
                            id="${task.taskId}">${task.taskId}</g:link></td>

                <td>${task.name}</td>
                <td><g:each in="${task.history}" var="h">
                    <g:formatDate date="${h.key}" format="dd/MM/yy hh:mm:ss"/>=${h.value}<br/>
                </g:each></td>
                <g:if test="${task.status}">
                    <td>${status[task.status.toInteger()]}</td>
                </g:if>
                <g:else>
                    <td>None</td>
                </g:else>

                <td><g:formatDate date="${task.created}" format="yyyy-MM-dd hh:mm:ss"/></td>
                <td><g:formatDate date="${task.activeThread}" format="yyyy-MM-dd hh:mm:ss"/></td>


                <td>
                    <g:link action="reRun" class="btn btn-sm btn-default" id="${task.taskId}"
                            params="${params}">re-run task</g:link>
                     <g:link action="cancel" class="btn btn-sm btn-default" id="${task.taskId}"
                            params="${params}">cancel</g:link>
                </td>
            </tr>
        </g:each>
        </tbody>
    </table>
</div>
</body>
</html>

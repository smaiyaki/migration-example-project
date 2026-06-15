package acme.dev

import com.adaptavist.hapi.cloud.jira.issues.Issues
import groovy.json.JsonOutput

// Non-HAPI way to do stuff, for the sake of using Mapping numeric IDs. Don't use at home, go HAPI
def issueKey1 = "COR-2"
def priorityId = "%priority-low%"

def response = put("/rest/api/3/issue/${issueKey1}")
        .header('Content-Type', 'application/json')
        .body(JsonOutput.toJson([
                fields: [
                        priority: [id: priorityId]
                ]
        ]))
        .asString()

assert response.status == 204 : "Failed to update priority: ${response.status} - ${response.body}"
logger.info("Priority updated to ID ${priorityId} on ${issueKey1}")

def issueKey2 = 'PROJ2-7'
def targetStatusId = "%status-inProgress%"

if (Issues.getByKey(issueKey2).statusId == targetStatusId) {
    logger.info("Issue IS in Progress")
} else {
    logger.info("Issue is NOT in Progress")
}

if (Issues.getByKey(issueKey2).fixVersions.any {it.id == "%version-1-0%"}) {
    logger.info("Oops, you got version 1.0")
}

def hapiIssue = Issues.getByKey(issueKey1)
def backEndComponentId = "%component-backend%"
def components = hapiIssue.getComponents()
logger.info("Components on ${issueKey1}:")
components.each { component ->
    if (component.id == backEndComponentId) {
        logger.info("Back End Component found:  ID: ${component.id}  |  Name: ${component.name}")
    }
}
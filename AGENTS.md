# ScriptRunner Dev and Deployment Tool - Agent Guide

This project deploys ScriptRunner Cloud configurations (listeners, script fields, jobs, behaviours, workflows, and Script Manager scripts) from a local Gradle project to a Jira Cloud instance.

Documentation: https://docs.adaptavist.com/sms/latest/scriptrunner-dev-and-deployment-tool

## Project Structure

```
cloud/src/main/groovy/                    # Groovy scripts for listeners, script fields, jobs, workflows, Script Manager
cloud/src/main/typescript/                # TypeScript scripts for behaviours only
cloud/src/main/resources/extensions.yaml  # All feature configuration (the central config file)
onprem/src/main/groovy/                   # Optional: Data Center scripts for side-by-side comparison (not deployed)
gradle.properties                         # Target Atlassian site URL
settings.gradle                           # Gradle plugin version
```

### Relevant external files

Typically, users will rely on automatic authentication from a browser prompt when running deployment commands, but for CI
environments, they may want to specify credentials using an Atlassian developer token. These can be written to a file in the
user home or passed as command line arguments via Gradle's `-P` flag.
```
~/.gradle/gradle.properties               # (Optional) User credentials (atlassianUser, atlassianToken) - NEVER commit
```

## The extensions.yaml File

All ScriptRunner Cloud configuration is defined in `cloud/src/main/resources/extensions.yaml`. This is the single source of truth for what gets deployed. The top-level keys are:

- `scriptfields` - Scripted fields that compute values on issue screens
- `listeners` - React to Jira webhook events
- `jobs` - Scheduled jobs AND escalation services (escalation services have a `jql` field)
- `behaviours` - TypeScript-based field visibility/validation on issue screens
- `workflows` - Conditions, validators, and post functions on workflow transitions
- `additional-scripts` - Scripts uploaded to Script Manager (not attached to any feature)

### Referencing Scripts in YAML

There are two ways to reference a script:

- **`file`** (preferred for all features except behaviours): Path relative to `cloud/src/main/groovy/`, supports subdirectories. Scripts are managed in Script Manager. Example: `file: "acme/dev/MyScript.groovy"`
- **`path`** (behaviours and legacy inline): Filename only, script must be directly in `cloud/src/main/groovy/` and gets inlined. For behaviours, this is relative to `cloud/src/main/typescript/`.

### UUID Rules

UUID fields are **identity fields**, not disposable placeholders:

- When converting Data Center configuration, preserve existing UUIDs 1:1
- Only generate a new UUID for genuinely new entries or to resolve duplicate collisions
- This applies to all UUID fields: top-level `uuid`, behaviour `fieldUuid`, workflow `parameters.id`, and `config.uuid`

### Feature Schemas

**Script Fields:**
```yaml
scriptfields:
  - name: "Field Name"
    uuid: "<uuid>"
    description: "optional"
    scriptedFieldType: "TEXT_FIELD"  # TEXT_FIELD, NUMBER_FIELD, DATE_FIELD, DATETIME_FIELD, RICH_TEXT_FIELD
    file: "MyScript.groovy"
```

**Listeners:**
```yaml
listeners:
  - description: "What it does"       # also used as Gradle task label
    uuid: "<uuid>"
    events:
      - jira:issue_created
    file: "MyScript.groovy"
    executionUser: ADD_ON              # ADD_ON or INITIATING_USER
    executionCondition: ""             # optional Jira expression
    projects:                          # optional; omit to run for all projects
      - "PROJ"
```

**Jobs / Escalation Services:**
```yaml
jobs:
  - uuid: "<uuid>"
    name: "Job Name"
    cronExpression: "0 0 0 1 * ?"     # 6-field cron: sec min hr dom mon dow
    file: "MyJob.groovy"
    atlassianUser:
      accountId: "<account-id>"
    enabled: true
    jql: "status = Done"              # add this field to make it an escalation service
```

**Behaviours (TypeScript):**
```yaml
behaviours:
  - name: "Behaviour Name"
    uuid: "<uuid>"
    enabled: true
    executionUser: ADD_ON
    mappings:                          # project key -> list of work item type names
      "PROJ":
        - "Task"
        - "Bug"
      "all":                           # wildcard for all projects (must specify work item types)
        - "Story"
    config:
      - fieldName: ""                  # leave empty; resolved via fieldUuid
        fieldUuid: "<uuid>"
        path: myBehaviour.ts           # relative to cloud/src/main/typescript/
        event: ["ONLOAD", "ONCHANGE"]
        viewTypes: ["GIC", "IssueTransition", "IssueView"]
        affectedFields: ["summary"]
```

**Workflows:**
```yaml
workflows:
  - name: "Exact Workflow Name"        # must match the Jira workflow name exactly
    transitions:
      - name: "Transition Name"
        sourceStatus: "In Progress"    # optional disambiguation
        targetStatus: "Done"           # optional disambiguation
        conditions:                    # Jira expression returning boolean
          - parameters:
              id: "<uuid>"
              config:
                uuid: "<uuid>"
                description: "Description"
                expression: "(issue.assignee != null)"
                enabled: true
        validators:                    # Jira expression returning boolean
          - parameters:
              id: "<uuid>"
              config:
                uuid: "<uuid>"
                description: "Description"
                expression: "(issue.assignee != null)"
                errorMessage: "Error shown to user"
                enabled: true
        postFunctions:                 # Groovy script
          - parameters:
              id: "<uuid>"
              config:
                className: "com.adaptavist.sr.cloud.workflow.UpdateIssue"  # always this value
                uuid: "<uuid>"
                enabled: true
                executionUser: "ADD_ON"
                condition: ""
                description: "Description"
                additionalCode:
                  file: "subdir/Script.groovy"    # OR inline with script: |
```

**Additional Scripts (Script Manager):**
```yaml
additional-scripts:
  - file: "acme/dev/ConsoleScript1.groovy"
```

## Identifier Mappings

DC entity IDs (custom fields, users, issue types, etc.) don't carry over to Cloud as-is. Use `%alias%` placeholders in scripts and `extensions.yaml`, defined in a mapping file at the project root. Run `./gradlew pullMappings` to resolve them into a `mapped/<subdomain>/` folder; deploy tasks automatically use that folder when present.

See [MAPPING_AC.md](MAPPING_AC.md) for the full spec: mapping file format, placeholder syntax, resolution logic, supported entity types, and validation rules.

## Deployment Commands

Requires JDK 17+. Authentication happens via browser popup (default) or API tokens in `~/.gradle/gradle.properties`.

```bash
# Bulk deployment
./gradlew cloud:deploy-all
./gradlew cloud:deploy-listener-all
./gradlew cloud:deploy-scriptField-all
./gradlew cloud:deploy-job-all
./gradlew cloud:deploy-behaviour-all
./gradlew cloud:deploy-workflow-all
./gradlew cloud:deploy-script-manager-all

# Individual deployment (name/description used as identifier)
./gradlew "deploy-listener-Add a comment on issue creation"
./gradlew "deploy-scriptField-Date Difference"
./gradlew "deploy-job-My Job Name"
./gradlew "deploy-behaviour-Test"
./gradlew "deploy-workflow-My Workflow Name"
./gradlew deploy-script-manager-acme-dev-ConsoleScript1.groovy
```

Listeners are identified by `description`. Script fields and jobs by `name`. Script Manager scripts by file path with `/` replaced by `-`.

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Supported Features

| Feature                    | Supported | Notes                                                      |
|----------------------------|-----------|------------------------------------------------------------|
| Listeners                  | Yes       |                                                            |
| Script Fields              | Yes       |                                                            |
| Jobs / Escalation Services | Yes       | Escalation services use `jobs` key with a `jql` field      |
| Behaviours                 | Yes       | TypeScript only; two behaviours cannot share the same name |
| Workflows                  | Yes       | Condition groups are now supported                         |
| Script Manager             | Yes       |                                                            |
| REST Endpoints             | No        | Not available in Cloud. Use ScriptRunner Connect           |
| UI Fragments               | No        | Planned                                                    |
| JQL Functions              | No        | Planned                                                    |
| Built-in Scripts           | N/A       | Nothing to configure in Cloud                              |

## Important Constraints

- Workflows must already exist in the target Cloud instance (typically migrated via Jira Cloud Migration Assistant)
- Behaviours use TypeScript, not Groovy. Scripts go in `cloud/src/main/typescript/`
- Strongly prefer the `file` attribute over `path` for all features except behaviours (keeps scripts in Script Manager)
- Escalation services share the `jobs` YAML key but appear separately in the ScriptRunner Cloud UI
- Always remind users to replace placeholder account IDs, project keys, project IDs, and issue type IDs with real values
- YAML errors will be logged but the plugin will still try to create tasks for valid configurations
- The TypeScript compiler may emit expected warnings during behaviour compilation that can be safely ignored

## Updating the Plugin

Edit `settings.gradle` and change the version in:
```gradle
plugins {
    id 'com.adaptavist.scriptrunner.migration-settings' version '1.2.0'
}
```

Check the [sample project on Bitbucket](https://bitbucket.org/adaptavistlabs/migration-example-project/src/main/settings.gradle) or the [Adaptavist Nexus repository](https://nexus.adaptavist.net/service/rest/repository/browse/external/com/adaptavist/scriptrunner/cloud/migration-dev-and-deployment-plugin/) for the latest version.

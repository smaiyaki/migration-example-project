# Identifier Mapping

When migrating from Jira Data Center, scripts and configuration files contain hard-coded DC entity identifiers
(custom field IDs, issue type IDs, user keys, etc.) that have different values in Cloud. The `pullMappings` Gradle task
resolves these by replacing `%placeholder%` markers in your source files with their Cloud equivalents, producing a
deployment-ready output folder.

---

## Mapping File

Create a JSON mapping file in the project root. Two formats are supported:

### Option A — Site-specific file (recommended)

**Filename:** `mappings-<subdomain>.json` (e.g. `mappings-my-company.json`)

Entity types are root-level keys:

```json
{
  "customFields": {
    "customField-budget": {
      "customfield_10001": "customfield_20001"
    }
  },
  "issueTypes": {
    "issueType-bug": {
      "10000": "10099"
    }
  },
  "projects": {
    "project-acme": {
      "10100": "10200"
    }
  },
  "users": {
    "user-jsmith": {
      "email/jsmith@example.com": "557058:abc123"
    },
    "user-jbloggs": {
      "email/jbloggs@example.com": "557058:def456",
      "jira.userkey/JIRAUSER1410": "557058:def456"
    }
  },
  "statuses": {
    "status-inProgress": {
      "10001": "10011"
    }
  }
}
```

### Option B — Shared multi-site file

**Filename:** `mappings.json`

Use this when working across multiple sites. Each subdomain is a root-level key:

```json
{
  "my-company": {
    "customFields": {
      "customField-budget": {
        "customfield_10001": "customfield_20001"
      }
    }
  },
  "my-company-staging": {
    "customFields": {
      "customField-budget": {
        "customfield_10001": "customfield_30001"
      }
    }
  }
}
```

The tool checks for a site-specific file first and falls back to `mappings.json` if not found.

### Structure

- Keys under an entity type are **named aliases** — these become your placeholder names.
- Every alias must start with the required prefix for its entity type (
  see [Supported Entity Types](#supported-entity-types) below).
- Under each alias, keys are **DC-side identifiers** and values are the corresponding **Cloud IDs**.
- An alias can have multiple DC-side keys if the same person or entity had multiple identifiers in DC — they all resolve
  to the same Cloud ID.
- For `users`, DC keys must be prefixed: `email/`, `jira.userkey/`, or `jira.username/`.

---

## Placeholder Syntax

Add `%alias%` markers in your YAML config files or any Groovy/TypeScript script wherever a DC identifier needs to be substituted. Always use them in **value positions**, never as YAML keys.

```groovy
// Groovy scripts
setCustomFieldValue("%customField-budget%", value)
setAssignee("%user-jsmith%")
```

```yaml
# any *.yaml under resources/
atlassianUser:
  accountId: "%user-jsmith%"

projects:
  - "%project-acme%"
```

The placeholder name must match an alias name **exactly** as defined in the mapping file.

---

## Running pullMappings

```bash
./gradlew pullMappings
```

The task reads `targetAtlassianSite` from `gradle.properties` to determine the subdomain, loads the mapping file,
resolves all `%placeholder%` markers, and writes the results to `mapped/<subdomain>/`.

`pullMappings` is **not** affected by `-Psource`. It always recursively scans and processes **all** `*.yaml` files
under `cloud/src/main/resources/` (including subdirectories).

```
mapped/
└── my-company/
    ├── extensions.yaml
    ├── jobs/
    │   └── scheduled.yaml
    ├── groovy/
    │   └── MyScript.groovy
    └── typescript/
        └── myBehaviour.ts
```

Source files are **never modified**. The `mapped/` folder is the only output.

Run `pullMappings` again any time the mapping file or source files change — the output folder is fully recreated each
run.

**Common errors:**

| Error                                  | Cause                                                                              |
|----------------------------------------|------------------------------------------------------------------------------------|
| No mapping file found                  | Neither `mappings-<subdomain>.json` nor `mappings.json` exists in the project root |
| Subdomain not found in `mappings.json` | The site subdomain is missing as a top-level key                                   |
| Unresolved placeholder `%xyz%`         | The alias `xyz` is not defined in the mapping file                                 |

---

## Deploy Tasks and pullMappings

Deploy tasks run `pullMappings` automatically when source files contain `%placeholder%` markers, then deploy from
`build/mapped/<subdomain>/`. With no placeholders, deploy reads from `cloud/src/main/` without running `pullMappings`.

> **Note:** Deploy hard-fails if unresolved `%placeholder%` markers remain in the files it reads.

### Skipping pullMappings

Pass `-PskipMappings` to skip the automatic `pullMappings` run. This only applies when source contains placeholders
and `build/mapped/<subdomain>/` already exists — deploy uses the existing mapped output and logs
that IDs may be stale. SkipMappings is only meant to be used if no changes were introduced since the last time
`pullMappings` was run. You cannot skip when source has placeholders but no mapped output yet.

```bash
./gradlew cloud:deploy-all -PskipMappings
```

---

## Validation Rules

The mapping file is validated when `pullMappings` runs, before any file is written. All errors are reported together so
you can fix them in one pass.

### Hard fails — task will not proceed

| Rule                                   | Detail                                                                                                                                                     |
|----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Valid JSON                             | The file must be parseable JSON.                                                                                                                           |
| Known entity type keys                 | Every top-level key must be one of the supported types listed below. Unknown keys fail.                                                                    |
| Alias names unique across the site     | No two aliases — regardless of entity type — may share the same name. e.g. you cannot have `customField-myField` in both `customFields` and `issueTypes`.  |
| Alias name starts with accepted prefix | Every alias must start with the required prefix for its entity type (see table below).                                                                     |
| DC keys unique within an entity type   | The same DC-side key cannot appear in two different aliases under the same entity type. This prevents one DC ID from resolving to two different Cloud IDs. |
| Valid user DC-key prefix               | Every DC key under `users` must start with `email/`, `jira.userkey/`, or `jira.username/`.                                                                 |
| No empty strings                       | Alias names, DC keys, and Cloud ID values must all be non-empty strings.                                                                                   |

### Warnings — logged but task continues

| Warning                                    | Detail                                                                                                                              |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Alias has no DC entries                    | An alias with no DC keys is likely a mistake, e.g. `"user-jsmith": {}`.                                                             |
| Same Cloud ID targeted by multiple aliases | Two or more aliases of the same type resolve to the same Cloud ID. Valid in multi-DC-to-single-Cloud migrations but worth checking. |

---

## Supported Entity Types

The **alias prefix** column shows the required prefix every alias name must start with for that entity type.
For `users`, DC keys must use one of the listed prefixes (`email/`, `jira.userkey/`, `jira.username/`).

| Mapping file key           | JCMA namespace                          | DC key prefix(es)                             | Alias prefix               |
|----------------------------|-----------------------------------------|-----------------------------------------------|----------------------------|
| `users`                    | `identity:user`                         | `email/` · `jira.userkey/` · `jira.username/` | `user-`                    |
| `groups`                   | `identity:group`                        | raw ID                                        | `group-`                   |
| `issues`                   | `jira:issue`                            | raw ID                                        | `issue-`                   |
| `comments`                 | `jira:comment`                          | raw ID                                        | `comment-`                 |
| `fileAttachments`          | `jira:fileAttachment`                   | raw ID                                        | `fileAttachment-`          |
| `filters`                  | `jira:filter`                           | raw ID                                        | `filter-`                  |
| `issueLinks`               | `jira:issueLink`                        | raw ID                                        | `issueLink-`               |
| `issueLinkTypes`           | `jira:issueLinkType`                    | raw ID                                        | `issueLinkType-`           |
| `portalPages`              | `jira:portalPage`                       | raw ID                                        | `portalPage-`              |
| `priorities`               | `jira:priority`                         | raw ID                                        | `priority-`                |
| `projectCategories`        | `jira:projectCategory`                  | raw ID                                        | `projectCategory-`         |
| `components`               | `jira:projectComponent`                 | raw ID                                        | `component-`               |
| `projectRoles`             | `jira:projectRole`                      | raw ID                                        | `projectRole-`             |
| `versions`                 | `jira:projectVersion`                   | raw ID                                        | `version-`                 |
| `resolutions`              | `jira:resolution`                       | raw ID                                        | `resolution-`              |
| `workLogs`                 | `jira/classic:workLog`                  | raw ID                                        | `workLog-`                 |
| `customFields`             | `jira/classic:customField`              | raw ID                                        | `customField-`             |
| `customFieldConfigSchemes` | `jira/classic:customFieldConfigScheme`  | raw ID                                        | `customFieldConfigScheme-` |
| `customFieldOptions`       | `jira/classic:customFieldOption`        | raw ID                                        | `customFieldOption-`       |
| `issueSecurityLevels`      | `jira/classic:issueSecurityLevel`       | raw ID                                        | `issueSecurityLevel-`      |
| `statuses`                 | `jira/classic:issueStatus`              | raw ID                                        | `status-`                  |
| `issueTypes`               | `jira/classic:issueType`                | raw ID                                        | `issueType-`               |
| `projects`                 | `jira/classic:project`                  | raw ID                                        | `project-`                 |
| `screens`                  | `jira/classic:screen`                   | raw ID                                        | `screen-`                  |
| `rapidViews`               | `jira/classic/software:rapidView`       | raw ID                                        | `rapidView-`               |
| `sprints`                  | `jira/classic/software:sprint`          | raw ID                                        | `sprint-`                  |
| `workflowRules`            | `jira/classic:workflowRule`             | raw ID                                        | `workflowRule-`            |
| `appCustomFields`          | `jira/classic:appCustomField`           | raw ID                                        | `appCustomField-`          |
| `requestTypes`             | `jira/servicedesk:requestType`          | raw ID                                        | `requestType-`             |
| `customerOrganizations`    | `jira/servicedesk:customerOrganization` | raw ID                                        | `customerOrganization-`    |

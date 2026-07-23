# ScriptRunner Migration Suite Development and Deployment Tool

This sample repository provides a template for developing, testing, and deploying scripts for ScriptRunner Cloud from a
local command line and IDE.

## Try it

You can clone this using Git (or your favorite Git GUI, such as SourceTree).
We recommend cloning to a different subdirectory for each migration you're working on. For example:

`git clone https://bitbucket.org/adaptavistlabs/migration-example-project.git migration-x`

### Install Java

You will also need to have a Java Developer Kit (JDK), version 17 or later installed to work with this project. We
recommend using [SDKMAN!](https://sdkman.io/) to manage Java installations. For example:

`sdk install java 17-tem`

### Point the Tool at your site

Update the gradle.properties file in this project to point to the Atlassian instance you want to work with.

```properties
targetAtlassianSite=https://your-instance-goes-here.atlassian.net
```

Also rename `mappings-target-instance-goes-here.json` to `mappings-<your instance name>.json` so that `pullMappings`
will work.

### Authenticate

To use the Development & Deployment Tool, the machine running the deployment commands has to be able to authenticate
with the ScriptRunner Cloud backend. There are two ways to do this:

1. Browser-based authentication (the default)
2. Saved Atlassian API Tokens

The browser-based path is probably easiest for most users, but is currently broken in Google Chrome and browsers based
on Chromium.
We know that sucks, and we're working on it, but there are some blockers. Please bear with us!

The second is best for people using the Dev & Deployment Tool in Continuous Integration environments who need a durable
stored credential (or need to work around the above bug for Chrome).

#### Path 1: Browser-based authentication

The first and easiest way to authenticate is to simply run commands and sign in to your Jira instance when a browser
window pops up. You'll see a message like the following:

> The ScriptRunner Dev & Deployment Tool is requesting access to your ScriptRunner instance.
> This will allow the Gradle plugin to deploy scripts and configurations on your behalf.
>
> Allow / Deny

Choose **Allow** and the Dev & Deployment tool will be able to use a temporary token to make updates as you.

This token expires after a while, so you may have to go through this routine again every day or so.

You can also wipe out the saved token by running `./gradlew clean`.

#### Path 2: Save Developer Credentials

If using the browser-based authentication doesn't work for your environment, you can instead create Atlassian API tokens
and store them in a private configuration file.

The configuration file you need to update will be in a different directory, from this project,
though it will also be named gradle.properties like the one you updated earlier. The directory is
the [Gradle user home directory](https://docs.gradle.org/current/userguide/directory_layout.html#dir:gradle_user_home),
which is a hidden directory inside your user home directory. Your user home directory will be in different locations,
depending on your operating system. Assuming your username is `me`, it might be /Users/home/me on Mac OSX,
C:\Users\me on Windows, or /home/me on Linux.

The Gradle user home directory's gradle.properties file is where you will put the credentials, which you want to keep
private to yourself.
In that file, you will specify the Atlassian User and Token that allow you to sign in to your Atlassian Cloud Site.

We will refer to the gradle.properties file in your user home directory as `~/.gradle/gradle.properties` for the rest of
this document. Different people working on this project will have different credentials. As such, they will each specify
their own credentials in their user home, while sharing the site URL in the project's local gradle.properties file.

##### Get the credentials

Create a new API token from [your Atlassian profile](https://id.atlassian.com/manage-profile/security/api-tokens). We
recommend creating a regular API token without scopes.

**Note:** Make sure you copy this token because it will not be visible once closed. We recommend saving it in a password
manager.

**Warning:** Atlassian API tokens allow a user to act as you on any instance connected to your Atlassian account.
Be as protective of this token as you would any password. Follow best practices and good hygiene for secrets management,
including revoking the access token when migration is done.

##### Update the files

Update your user home gradle.properties file `~/.gradle/gradle.properties` with the following properties set (without
the square brackets):

```properties
atlassianUser=[your Adaptavist email address]
atlassianToken=[your token from the previous step]
```

Keeping your credentials in your user home gradle.properties file (`~/.gradle/gradle.properties`) will allow you to
reuse them across multiple projects. Setting the target instance in the local gradle.properties file will make it easier
if you have multiple engagements in flight, each one in different directories. It will also save you from publishing
them somewhere that you shouldn't.

## Configuration and Code

The Groovy code for your listeners can go in the cloud/src/main/groovy folder.

As an optional step, you may put your DataCenter scripts in the onprem/src/main/groovy directory.
This is to help with migrations and allow a side-by-side comparison of DataCenter and Cloud scripts, but it is not
necessary at all in order to use the Development and Deployment Tool.

ScriptRunner Cloud configuration lives under `cloud/src/main/resources/` as one or more `*.yaml` files.
A single `extensions.yaml` still works; for larger migrations you can split config across multiple files and
subdirectories (for example by team or feature type). By default, deploy tasks recursively scan **all** `*.yaml`
files under `resources/`.

### Splitting configuration across multiple YAML files

Large single YAML files are hard to review and own. You can split configuration into as many `*.yaml` files as you
like under `cloud/src/main/resources/`, including nested folders. Configs from every file in scope are merged before
deploy. When more than one file is in scope, duplicate identities (same `uuid`, or same `name`/`description` within a
feature type) **block** deployment.

Example layout:

```
cloud/src/main/resources/
├── extensions.yaml                 # e.g. listeners, script fields
├── jobs/
│   ├── scheduled.yaml
│   └── escalations.yaml
└── team-a/
    ├── behaviours.yaml
    └── nested/
        └── workflows.yaml
```

#### Scoping with `-Psource`

Use the Gradle property `-Psource` to limit which YAML files drive **task registration**, `./gradlew tasks`, and
**deploy**. Paths are relative to `cloud/src/main/resources/`.

| Mode | Example | Scope |
|------|---------|--------|
| Default (no `-Psource`) | `./gradlew cloud:deploy-all` | All `*.yaml` under `resources/` (recursive) |
| Single file | `-Psource=extensions.yaml` | That one file only |
| Subdirectory | `-Psource=jobs/` or `-Psource=team-a/` | All `*.yaml` under that directory (recursive) |

`-Psource` affects deploy and tasks listing. It does **not** affect `pullMappings` — that always recursively processes
all YAML (and Groovy/TypeScript) under `resources/`. See [MAPPING.md](MAPPING.md).

```bash
# All yaml under resources/ (recursive) — includes jobs/, team-a/, team-a/nested/, …
./gradlew cloud:deploy-all

# Single file only
./gradlew cloud:deploy-all -Psource=extensions.yaml

# All yaml under jobs/ (scheduled.yaml + escalations.yaml)
./gradlew cloud:deploy-job-all -Psource=jobs/

# All yaml under team-a/ recursively (behaviours.yaml + nested/workflows.yaml)
./gradlew cloud:deploy-all -Psource=team-a/

# tasks listing matches the same scope
./gradlew cloud:tasks -Psource=team-a/
```

## Use an IDE

Any IDE with Gradle and Groovy support can add value to this project, but we
recommend [IntelliJ](https://www.jetbrains.com/idea/download/?section=mac). The free Community Edition supports
everything that you need to use this project.

Double-check that the JDK IntelliJ is using matches the one that you are using. There will be a couple of places to
check:

* The [IntelliJ Gradle Project Settings](https://www.jetbrains.com/help/idea/gradle-settings.html)
* The [IntelliJ Project Structure Settings](https://www.jetbrains.com/help/idea/project-settings-and-structure.html)

While IntelliJ will typically pickup and use sensible defaults based on your environment and this project, you may have
different defaults, especially if you're using Java or IntelliJ in other projects.

## Identifier Mapping (DC → Cloud)

When migrating from Data Center, entity identifiers (custom field IDs, user keys, issue type IDs, etc.) differ in
Cloud — and IDs can vary between Cloud sites as well. Rather than hard-coding values in scripts or YAML config files,
we suggest using `%placeholder%` markers backed by a mapping file that records each DC → Cloud translation.

Deploy tasks run `pullMappings` automatically when source contains `%placeholder%` markers, then deploy from
`build/mapped/<site>/`. With no placeholders, deploy reads from `cloud/src/main/`. You can also run `pullMappings`
standalone to inspect resolved output before deploying.
See **[MAPPING.md](MAPPING.md)** for the mapping file format, placeholder syntax, and supported entity types.

```bash
./gradlew pullMappings          # optional — inspect resolved output
```

> Deploy tasks hard-fail if unresolved `%placeholder%` markers remain in the files they read. Pass `-PskipMappings`
> only when source has placeholders, `build/mapped/<site>/` already exists, and you accept deploying possibly stale
> resolved IDs.

---

## Automate Deployment

Scripts and their configuration can be deployed to the configured cloud instance using various Gradle tasks.

You'll need to define the configuration for each script manually in one or more YAML files under
`cloud/src/main/resources/` (see [Splitting configuration across multiple YAML files](#splitting-configuration-across-multiple-yaml-files)).
Most features are supported, with the notable exceptions of Script Fragments, though support for those is on the roadmap.
See https://docs.adaptavist.com/sms/latest/scriptrunner-dev-and-deployment-tool/supported-features-and-limitations for
the up-to-date list of supported features.

NOTE: Escalation Services are grouped under the `jobs` anchor in the YAML configuration. 
They will be in a distinct spot in the ScriptRunner Cloud UI once deployed. See the [feature documentation](https://docs.adaptavist.com/sr4jc/latest/features/escalation-service) for more details.

There are examples in there to start you off, but don't keep them unless you *actually* want those scripts in your
instance. Likewise, make sure to carefully verify any configuration you get from the Migration Agent or other AI-based
tools.

Once you have configuration in those YAML files, the project will automatically configure Gradle tasks for each
script configuration in scope. Tasks can either be run from the command line (eg `./gradlew cloud:deploy-all`) or from the Gradle
tool window in IntelliJ IDEA (or your IDE of choice). There are tasks to deploy scripts in bulk:

    ./gradlew cloud:deploy-all # Deploy all extensions
    ./gradlew cloud:deploy-behaviour-all # Deploy all behaviours
    ./gradlew cloud:deploy-listener-all # Deploy all listeners
    ./gradlew cloud:deploy-scriptField-all # Deploy all script fields
    ./gradlew cloud:deploy-job-all # Deploy all jobs
    ./gradlew cloud:deploy-workflow-all # Deploy all workflows
    ./gradlew cloud:deploy-script-manager-all # Deploy all additional script manager scripts 

There will also be tasks to deploy individual scripts. This can be useful if you only want to verify your most recent
change is correct. For example, some of our pre-configured samples will generate tasks like these:

    ./gradlew "deploy-job-Create time logging issue"
    ./gradlew "deploy-scriptField-Date Difference"
    ./gradlew "deploy-listener-Add a definition of done checklist to an issue on creation"
    ./gradlew "deploy-workflow-SMURF_ Project Management Workflow"
    ./gradlew deploy-script-manager-acme-dev-ConsoleScript1.groovy

For the Gradle task name, listeners are identified by their description. Script fields and script jobs by their name.
Additional script manager scripts are identified by their file path with forward slashes `/` replaced by hyphens `-`.

You can also get a list of all valid tasks, including individual scripts, running ` ./gradlew cloud:task`

If there are errors in the YAML, the plugin will fail to apply and log out any errors received on any Gradle task.

## Dynamic Members for Better Autocompletion

To provide better autocompletion for scripts, we provide some context for the variables and methods that are always
imported in ScriptRunner. This includes the `logger`, your `baseUrl`, and
the [methods from Unirest](https://docs.adaptavist.com/sr4jc/latest/get-started/technical-background#unirest) which are
imported automatically into most script contexts. These are defined in the `cloud/src/main/groovy/cloud.gdsl` file,
which uses [IntelliJ IDEA's GroovyDSL Scripting Framework](https://youtrack.jetbrains.com/articles/GROOVY-A-15796912).

You may want to set up more dynamic members for a specific script to aid development.
See https://youtrack.jetbrains.com/articles/GROOVY-A-12779640 for details on how to do that via the IntelliJ UI.

You may want some of the following, depending on the context you are working in:

| Name         | Type                                             | Contexts where useful                                                 |
|--------------|--------------------------------------------------|-----------------------------------------------------------------------|
| issue        | com.atlassian.jira.rest.clientv2.model.IssueBean | Escalation Services                                                   |
| issue        | java.util.Map                                    | Workflow functions, Listeners on issue-specific events, Script Fields |
| webhookEvent | java.lang.String                                 | Listeners                                                             |
| timestamp    | java.lang.String                                 | Listeners                                                             |

# Get Updates

A Gradle plugin developed by Adaptavist powers deployments in this project. We are actively working on it, so you will
need to apply updates to get additional feature support and bugfixes as we release them.

To update, open the `settings.gradle` file in this project and edit this set of lines:

```gradle
plugins {
    id 'com.adaptavist.scriptrunner.migration-settings' version '1.5.0' // Change this version number to the latest release
}
```

You can check back in
the [sample project on Bitbucket](https://bitbucket.org/adaptavistlabs/migration-example-project/src/main/settings.gradle)
or look at
the [plugin artifact in our public nexus repository](https://nexus.adaptavist.net/service/rest/repository/browse/external/com/adaptavist/scriptrunner/cloud/migration-dev-and-deployment-plugin/)
to get the latest version number of the plugin.

# Further information

For further information read the [documentation on our website](https://docs.adaptavist.com/sms/).

## Working with workflows

Our assumption is that you will have already run the Jira Cloud Migration Assistant for your instance, and that all of
your workflows already exist in your target Cloud instance. Likewise, we assume that other apps and Jira native workflow
conditions, validators, and post functions will exist alongside ScriptRunner workflow functions, but those will not be
managed in the extensions.yaml file.

If you have ScriptRunner workflow functions in a workflow that aren't part of the Dev and Deployment's configuration
file (extensions.yaml),
the Dev and Deployment tool will log out a warning when you run deployment tasks. It won't hurt anything, but it can be
helpful to have all of the configuration in one place.

The reverse case is also warned: if a transition declared in your YAML does not match any transition on the Jira
workflow (wrong name and/or source/target statuses), its ScriptRunner functions are **not** deployed and a warning is
logged. The build still succeeds — check the log so those functions are not silently skipped.

### Ordering post functions and validators

You can control placement for both `postFunctions` and `validators` by using a `placement` block on a ScriptRunner rule.

Supported placement fields:

- `index` (0-based absolute position)
- `first`
- `last`

⚠️ All placement fields are mutually exclusive, only one of them can be used each time.

# Known Issues

## Rate Limits

Both Atlassian and Adaptavist have some rate limits on our APIs.

The place where this is most keenly felt is in workflow deployments. Customers often have a large number of workflows
and may run into Atlassian's rate limits at scale.

Atlassian's rate limits are documented at https://developer.atlassian.com/cloud/jira/platform/rate-limiting/.

We have plans to build rate limiting into the tool more thoroughly, so watch this space
and [our documentation](https://docs.adaptavist.com/sms/latest/scriptrunner-dev-and-deployment-tool/supported-features-and-limitations).

## TypeScript Compilation Warnings

For Behaviours, the TypeScript compiler may emit some errors during compilation of the TypeScript code into JavaScript.
Some of these errors may indicate problems in your script, but some can be safely ignored.

Specifically, warnings like these are expected. We're working on better ways to suppress them.
🚧 Please pardon our progress. 🚧

```properties
../../src/main/typescript/behaviours.d.ts(388,5):error TS1038: A 'declare' modifier cannot be used in an already ambient context.
../../src/main/typescript/behaviourA.ts(14,15):error TS1378: Top-level 'await' expressions are only allowed when the 'module' option is set to 'es2022', 'esnext', 'system', 'node16', 'node18', 'node20', 'nodenext', or 'preserve', and the 'target' option is set to 'es2017' or higher.
```

# Optional Analytics

The Gradle plugin collects some high-level analytics about its usage.
We use these to gauge how people are getting on with the Dev and Deployment tool and make it work better.
You can disable these by adding the following property to gradle.properties:

```properties
com.adaptavist.scriptrunner.deployment.analytics.enabled=false
```

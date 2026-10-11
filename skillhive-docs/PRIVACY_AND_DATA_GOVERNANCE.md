# Privacy and Data Governance Policy

Last updated: October 11, 2026

## Purpose and scope

SkillHive is open-source, self-hosted software that lets a team share reviewed
agent skill packages and keep working files in knowledge bases (a team file center
with folders, previews, and version history). It is derived from the open-source
SkillHub project. This document describes the project's privacy and
data-governance expectations and the controls available to people who operate
SkillHive instances.

The SkillHive maintainers publish source code and project infrastructure. They do
not operate or control every independently hosted instance. The organization or
person operating an instance determines why and how personal data is processed in
that environment and is responsible for publishing an instance-specific privacy
notice, selecting lawful processing grounds, handling data-subject requests, and
complying with applicable law.

Each instance shows an in-product Privacy Policy and Terms of Service (`/privacy`
and `/terms`) for its end users. This project document supplements them for
source-code reviewers and self-hosted operators. It is not legal
advice and does not certify that every deployment is automatically compliant with
any law.

## Applicable law

Privacy and data-protection obligations depend on the operator's legal entity,
where the instance and its users are located, the people it serves, the data in
skill packages, and the infrastructure and integrations selected by the operator.

Before processing personal data, each operator must:

- identify and document the domestic and international laws that apply;
- determine and record the lawful basis for each material processing purpose;
- complete any required privacy, child-safety, security, or transfer assessment;
- reflect those obligations in notices, contracts, procedures, and configuration;
  and
- avoid or redesign processing that cannot be operated lawfully.

Open-source availability and configurable controls support implementation. They
do not replace an operator's legal analysis or operational responsibilities.

## Data the software can process

The exact data depends on the authentication, storage, email, observability,
scanner, and deployment options selected by the operator. A SkillHive instance can
process:

- account and identity data, such as username, email address, avatar, OAuth
  provider identifiers, account status, platform roles, namespace membership, and
  per-member edit and download permissions;
- authentication and security data, such as session identifiers, password hashes,
  API token metadata (name, prefix, scopes, expiry, last use), login events, IP
  addresses, device or browser information, and password-reset records;
- skill package content, including `SKILL.md`, scripts, documentation, images,
  examples, license files, archives, and version metadata;
- knowledge base content: uploaded files of any supported type (documents,
  spreadsheets, images, archives), their titles, descriptions, folders, and every
  stored version;
- collaboration and governance data, such as namespaces, reviews, review comments,
  promotion requests, reports, ratings, stars, notifications, and audit records;
- access records, such as knowledge file downloads and previews and skill file
  reads or package downloads made with API tokens, each recorded with the user and,
  for token access, the token used;
- usage and operational data, such as searches, downloads, request identifiers,
  timestamps, errors, metrics, traces, application logs, and security findings;
  and
- configuration and connection data for object storage, identity providers, email,
  monitoring, and optional scanning services.

A skill package, review comment, profile, log entry, or security report can contain
personal, confidential, or authentication data even when a field is not labelled
as personal data. Operators and publishers must classify data according to its
actual content and use.

## Roles and responsibilities

For an independently operated instance, the instance operator normally decides the
purposes and means of processing and must document its role under applicable law.
Publishers and namespace administrators are responsible for the content they upload
and the access decisions they make. External identity, storage, email, monitoring,
and scanner providers may process data under their own terms and assigned roles.

The SkillHive maintainers generally cannot access, correct, export, or
delete data held by an independently operated instance. Requests concerning an
instance must go to the operator identified in that instance's privacy notice.

## Purpose limitation and data minimization

Data should be collected only when needed to authenticate users, enforce access
rules, publish and distribute skill packages, operate review and governance
workflows, secure and troubleshoot the instance, and meet documented legal duties.

Operators, administrators, and publishers should:

- avoid placing secrets or unnecessary personal data in skill packages, README
  files, examples, knowledge base files, namespace profiles, reviews, or report
  details;
- use pseudonymous or organization-scoped identifiers where practical;
- configure the shortest retention and least visibility needed for each purpose;
- redact sensitive values before sending packages or findings to an external
  scanner, model, log sink, or support channel;
- restrict privileged roles and review them regularly; and
- document the source, purpose, recipients, lawful basis, and retention period for
  each material category of personal data.

## Storage, access, and isolation

SkillHive supports authenticated access, platform and namespace RBAC, per-member
edit and download permissions, public, namespace-only and private skill
visibility, knowledge bases that are never open to anonymous users, audit logs,
hashed and scope-limited API tokens (`skill:*`, `knowledge:read`), PostgreSQL,
Redis, and local or S3-compatible object storage. API tokens read knowledge with
team membership only: platform roles such as `SUPER_ADMIN` do not widen what a
token can read. These capabilities are building
blocks, not a secure deployment by themselves.

Operators are responsible for:

- disabling development authentication and replacing example credentials before
  exposing an instance;
- using HTTPS for external traffic and protected networks for internal services;
- applying least-privilege roles to users, services, databases, caches, and object
  stores;
- encrypting sensitive data and backups according to their threat model and legal
  obligations;
- storing secrets in an appropriate secrets manager rather than source code, skill
  packages, client-side configuration, or logs;
- testing namespace and object-storage isolation for their configuration;
- restricting and monitoring access to packages, audit data, logs, traces, backups,
  and security findings; and
- applying supported security updates and maintaining a recovery process.

## External services and international transfers

OAuth providers, S3-compatible storage, email services, monitoring systems,
mirrors, and optional scanner integrations can receive data from a SkillHive
instance. An operator that enables an external service must assess its privacy and
security terms, hosting locations, retention, subprocessors, training-data rules,
and cross-border transfer mechanism.

The optional scanner can process uploaded skill archives and findings. If an
operator enables an external or LLM-backed scanner, that disclosure must be covered
by the instance's privacy notice and data-flow review. A service does not become
private merely because SkillHive can integrate with it.

Users can let scripts and AI tools read skills and knowledge files with their own
API tokens, for example through the read-only MCP server in `mcp/`. Content read
this way leaves the instance and is processed by the tool and model provider the
user chose. Operators should state which AI tools and token scopes are acceptable,
prefer read-only tokens, and use the audit log (which records the token behind each
read) to review such access.

## Retention, deletion, and portability

The open-source project does not impose one retention period on independently
operated instances. Each operator must publish periods that are no longer than
necessary for its purposes and legal duties.

Deleting a knowledge file archives it: its versions and stored objects are kept
until the operator removes them. A deletion process should cover account and
namespace records, package objects, knowledge files and their versions,
reviews, reports, ratings, notifications, security findings, caches, audit records,
logs, traces, exports, and backups. Where immediate backup deletion is not
practical, deleted data should be isolated from normal use and expire under a
documented schedule. Operators must also account for copies already disclosed to
external providers or downloaded by authorized users.

Operators should provide authenticated channels for access, correction, deletion,
restriction, objection, and portability requests where applicable. Requests should
be verified, recorded, completed within legally required time limits, and denied
only on a documented basis.

Skill packages can be downloaded as archives and knowledge files in their original
format. The `/api/v1` APIs and the read-only MCP server expose package, file, and
metadata reads. Operators should separately document
which instance records can be imported or exported, their non-proprietary formats,
version compatibility, and any PII excluded from an export.

## Security and incident handling

Report security vulnerabilities in SkillHive privately through
[GitHub security advisories](https://github.com/kun001/skillhive/security/advisories/new),
not in a public issue. Vulnerabilities that also affect the upstream SkillHub code
can additionally be reported under the
[iFLYTEK organization security policy](https://github.com/iflytek/.github/blob/main/SECURITY.md).

Instance operators remain responsible for monitoring their environments,
maintaining an incident-response plan, preserving proportionate evidence, rotating
affected credentials (including revoking leaked API tokens), applying fixes, and
notifying users or authorities where required.

## Project and instance contacts

- Report a SkillHive security vulnerability privately through
  [GitHub security advisories](https://github.com/kun001/skillhive/security/advisories/new).
- Ask questions about this project policy in
  [GitHub issues](https://github.com/kun001/skillhive/issues). Do not include
  personal data or confidential incident details in a public issue.
- Contact the operator named in an instance's privacy notice for data-subject
  requests or incidents involving that instance.

## Governance and changes

Privacy-impacting changes should be reviewed for data minimization, access and
namespace boundaries, package visibility, knowledge access, token scopes, external
disclosures, retention,
logging, deletion, and portability. Material changes to this document are made
through the repository's public review process, and the file history records them.

# Triage Labels

The skills speak in terms of five canonical triage roles. This repo tracks issues as markdown
files (see `issue-tracker.md`), so a "label" here is the value written on the `Status:` line near
the top of the issue file, e.g.:

```markdown
Status: needs-triage
```

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), write the corresponding
string from the "Label in our tracker" column onto the issue file's `Status:` line.

Edit the "Label in our tracker" column to match whatever vocabulary you actually use.

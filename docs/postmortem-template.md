# Incident Postmortem

## Summary

- Incident title:
- Start time:
- End time:
- Severity:
- Incident commander:
- Affected services:

## Customer impact

Describe who was affected, the number of failed or delayed requests, any booking-correctness impact, and the duration.

## Detection

Describe the first signal, the alert that should have fired, and any detection gap.

## Timeline

| Time | Event |
| --- | --- |
| 00:00 UTC | Initial event |

## Root cause

Explain the technical and organizational causes. Distinguish the triggering event from the conditions that allowed it to cause impact.

## Resolution and recovery

Describe containment, repair, data validation, and the evidence used to declare recovery.

## What went well

- Item

## What went poorly

- Item

## Corrective actions

| Action | Owner | Priority | Due date | Tracking link |
| --- | --- | --- | --- | --- |
| Example action | Team | High | YYYY-MM-DD | Issue URL |

## Correctness review

- Were any seats double-booked?
- Were any payments captured more than once?
- Were any confirmed orders missing an outbox event?
- Were all manual data changes audited?

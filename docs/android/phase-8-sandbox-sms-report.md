# Phase 8 sandbox SMS parser correctness report

This remains sandbox work. It does not establish production SMS parsing readiness.

## Migration behavior

Room 2→3 preserves every v2 `sms_dedupe` row while adding the provider column. Because v2
did not record provider, migrated rows use `legacy:<messageHash>` as an unambiguous legacy
namespace. Their message hashes continue to participate in duplicate lookup. The migration
creates a replacement dedupe table, copies rows, drops only the old dedupe table, and renames
the replacement. It never drops, recreates, deletes from, or otherwise modifies `queue`, so
existing evidence queue data remains intact. This is a table-rebuild migration with preserved
compatible dedupe data; it is not described as inherently non-destructive.

## Dedupe atomicity and message-hash semantics

`ParserRepository.enqueueSms` prepares the encrypted `QueueItem` before entering the DAO
transaction. `QueueDao.enqueueAndDedupeIfNew` then performs duplicate lookup, queue-capacity
check, queue insert, and dedupe insert in one Room transaction. A duplicate commits neither
insert. Any failure during either insert rolls back both. The logical evidence keeps its one
generated ingestion ID in the inserted queue row.

The database primary key uniquely enforces `(provider, transactionId)`. Duplicate lookup also
checks `messageHash` inside the serialized Room transaction, preventing duplicate enqueue
through this application path. There is no independent `UNIQUE(messageHash)` database
constraint, so direct out-of-band database writes are not protected by such a constraint.

## Instrumentation coverage

Instrumentation uses an in-memory Room database and a paired lower-level test repository,
which isolates the worker processor from persisted app pairing state. It asserts worker
`Result`, queue and dedupe counts, successful dedupe existence, duplicate suppression, no
dedupe after failed enqueue, and successful retry after pairing. It does not spoof Android's
protected SMS broadcast.

## Runtime status

**REAL SMS_RECEIVED PATH: UNVERIFIED.** Synthetic UI/worker injection and direct processor
tests are not equivalent to a genuine system-delivered `SMS_RECEIVED` intent. Verification
requires an actual SMS delivered to a permission-granted connected test phone and observed
processing by the registered `BroadcastReceiver`; that evidence has not been produced.

The receiver parses in memory and passes normalized fields to WorkManager. Raw SMS text is
not stored in WorkManager or Room and is not logged. Parser filters and sandbox sender/
transaction conventions remain in place. Production traffic and verification remain disabled.

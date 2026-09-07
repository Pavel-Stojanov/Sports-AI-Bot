# Demo walkthrough

Show the bot working, then use the trace and code to explain its decisions.
Keep a completed session available before meeting the TA. A live LLM request can
be slow or rate limited even when the application works.

## Before the meeting

1. Select Java 21, start PostgreSQL, then start the backend and frontend using
   the README commands. Run `./mvnw test`, `npm run build` and `npm run lint`.
2. Check that the backend `.env` has the intended LLM configuration and Vezilka
   key. Keep that file and request headers out of the screen you share.
3. Run one gol.mk session beforehand. Check its articles against their source
   links and keep its trace available as a fallback.
4. Prepare one draft donation batch with reviewed article text. Submitting it
   during the demo writes to the real corpus. An existing result is enough to
   explain the integration if a live submission is unnecessary.

## Walk through the application

### Create and run a session

Open Sessions, create a session with a FEED_URL target such as
`https://www.gol.mk/fudbal`, then open its details and press Start.

Explain that a target defines the goal. FEED_URL starts from a page, HASHTAG is a
section name, KEYWORD is a search topic, and PROFILE is a team or competition.
gol.mk is public, so the login hook opens the homepage without entering credentials.

Watch the trace update. Pick a NAVIGATE and an EXTRACT action and explain their
relationship to the page. The LLM chooses the next action from a text snapshot
with annotated links. Playwright executes that action in Chromium.

### Show control over the run

Press Stop while the session is active. It becomes PAUSED. The current browser
or LLM operation must return before the worker stops at an action boundary.
Resume restarts navigation and preserves posts already saved by completed
targets. An interrupted target is extracted again, with duplicate IDs filtered
within the session.

Each start gets an execution number. If an old request returns after Resume,
it cannot change the new run's status. Sessions execute one at a time so they
do not share a browser concurrently.

### Inspect an extracted post

Open Posts and show filtering, pagination and a source link. Open a post's
details and distinguish the article text from the AI-generated summary.
Donations send the article text. The summary helps someone browse the results.

Explain the language score as a heuristic based on script and common words.
A score of 1.0 is not proof of language or a calibrated probability. The client
requires a score of at least 0.6 for donation, and Vezilka makes the final decision.

Images belong to the local content browser. Donation scope for this project is
text. Do not claim that the supplied API document has no media endpoints; it
describes separate endpoints and authentication restrictions.

### Explain the donation workflow

Create a batch, review it, then approve it. Approval is a local state change.
Submit sends the original extracted text and source URL to Vezilka.

Show accepted, rejected and pending counts. Open a rejected post to show its
reason. HTTP 200 can mean that every item was rejected, so the service reads
individual results. Assignment to a draft batch is not acceptance.

If part of a batch fails, the known verdicts remain stored. Only unsent posts
retry after the server's delay. A rejection without an ID is still final.
Duplicates count as accepted because the content is already present.

## Code to have ready

| Question | File to open |
|----------|--------------|
| Where is the supplied loop? | `bot/core/AbstractSocialNetworkBot.java` |
| How does the browser perceive a page? | `bot/browser/PlaywrightBrowserAgent.java` |
| How does the LLM choose an action? | `bot/llm/OpenAiCompatibleLlmClient.java` |
| What changes for gol.mk? | `bot/core/GolMkBot.java` |
| How are articles extracted? | `bot/extraction/GolMkContentExtractor.java` |
| Who persists posts and logs? | `bot/core/BotOrchestratorImpl.java` |
| How do retries avoid resending known results? | `service/domain/impl/DonationServiceImpl.java` |
| How is the API contract preserved? | `integration/vezilka/BatchVezilkaClient.java` |
| How are old records upgraded? | `src/main/resources/db/migration/V8__track_execution_and_donation_progress.sql` |

Java paths in this table are relative to
`ai-bot-backend/src/main/java/mk/ukim/finki/aibotbackend/`.
The migration path is relative to `ai-bot-backend/`.

## Questions to rehearse aloud

**Why use an LLM to navigate?** The assignment asks for a bot that chooses actions
from page context. The model interprets the goal and page. Java handles execution,
validation, retry timing and state transitions.

**What happens when the model returns invalid JSON?** The client validates the
required fields and allows one repair attempt. A second invalid response fails
the session and appears in the trace.

**What does COMPLETED mean?** The run ended with saved content. It does not prove
that the bot found every relevant article. The loop also has a step limit.

**Can the model make mistakes?** Yes. It can revisit links, choose irrelevant
pages or extract incomplete text. Snapshots are limited to 10,000 characters,
and generated summaries need review. Source links make checking possible.

**What happens after a server restart?** Browser state is not restored. Stop and
resume a session left RUNNING. The implementation is intended for one backend
instance, not a distributed queue.

**How did you test the fixes?** Show tests for queued runs, pause/resume races,
partial donation retries, rejected items without IDs, persisted retry timestamps,
and migration of an old partial batch. The HTTP tests use a local server, so
they exercise request formatting without donating test data.

## Observed verification

The readiness check on 2026-09-08 passed 67 backend tests with no skips. The
frontend build and lint passed. Browser checks with mocked API responses covered
selection across 51 posts, preserving a failed selection, rejection details, and
stop/resume controls.

A separate live run against gol.mk saved three articles in a disposable database.
It used a 12-step limit and made no donations. The trace included a repeated link
and an extraction attempt on a scoreboard page, which returned no article.
This is a useful example of why prompts, extraction checks and execution limits
all matter. Live Vezilka acceptance was not verified in that check.

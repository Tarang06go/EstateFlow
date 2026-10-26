# Phase 9 — Property Assistant

## Design and supported questions

The assistant uses deterministic local routing, not a generative model. `PropertyAssistantService` selects bounded read queries from the existing authorized services. It can list tenant and owner names/status, active/vacant/occupied properties, agreements, valid payments, open maintenance requests and work orders; summarize rent collection and current arrears, upcoming lease expirations, occupancy, portfolio actuals, expenses, deposits, and cash-basis financials; and rank property receipts. Lists are capped, sensitive tenant contact/identity fields are omitted, and tenant-identifying arrears require `VIEW_TENANT_INSIGHTS`.

Financial responses distinguish charges by due date, valid receipts by payment date, recorded expenses, and NOI. Monthly trends use reported actuals. Optional run-rate estimates are labeled as estimates, never predictions; reported data cannot provide a causal explanation for an income change, so the assistant says when the cause is not recorded.

Curated help answers explain the current add-property, tenant, agreement, rent/payment, void, maintenance, reports, metrics, and permission workflows. A whitelist allows direct navigation to supported screens/forms; `SceneNavigator` enforces screen permissions and existing forms/services keep their normal validation and confirmation. The assistant itself does not create, edit, void, delete, or run arbitrary SQL/shell commands.

The interface `GeneralAssistantProvider` is the extension point for unrelated general questions. Configuration is `assistant.provider=none` in `config/application.properties`; `none` is currently the only supported value. No provider/key is configured, no external service is called, and no business records are sent to an AI. A future provider must be separately implemented and reviewed; business-data answers must continue to use the authorized service layer.

Follow-up context stores only the current user's last query topic, date window, and lease window in memory. It contains no result records, is not persisted, and is cleared on logout. Questions and replies are not sent externally.

## Authorization and privacy

The assistant checks `VIEW_DATA` before processing. Each data service repeats its own authorization. Tenant arrears require `VIEW_TENANT_INSIGHTS`. Full-page access requires `VIEW_REPORTS`. Whitelisted navigation is guarded again by `SceneNavigator`; write actions are not implemented in chat. Questions are limited to 500 characters. The response includes its source and generation time.

## Verification

Unit tests cover tenant/property lists, portfolio and rent answers, tenant-insight permission enforcement, lease windows, follow-up dates, safe navigation, general-provider configuration, unsupported and empty cases, and existing summary flows. Run `mvn verify` for current project verification results. No database migration is needed. FXML parses as XML; a real-display UI check remains outstanding.

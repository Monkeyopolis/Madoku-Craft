## Madoku Craft: HUD

The HUD module depends only on Core and can be installed independently.

Health, hunger, armor, oxygen, and luck presentation is owned by HUD and is
read from vanilla client state, so HUD works without the Attributes module.
When Attributes is present, the optional Compat module synchronizes extended
server-authoritative values such as custom hunger limits.

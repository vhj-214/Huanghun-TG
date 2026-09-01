# Collectible gift restart investigation

## Root cause

The local profile gift record stores a collectible's unique ID, base catalog gift ID, document ID, colors, pattern document ID, title, slug, and number. The session-only gift map contains the complete Telegram `TL_starGiftUnique` object while the process is alive.

After a process restart, the session map is empty. The old `getSessionGift()` implementation then used `catalogGiftId` as a generic lookup and returned the base `StarGift`. That object is the ordinary catalog gift, not the purchased `TL_starGiftUnique`. `ProfileGiftsContainer.buildOfficialSavedGift()` therefore never entered its unique reconstruction branch. `GiftCell` and `StarGiftSheet` received an ordinary gift, so the white card, ordinary price presentation, missing unique ribbon/details, and missing collectible backdrop were all expected consequences of the wrong runtime type.

A second persistence issue made the failure harder to recover from: the purchase path did not save the pattern document ID, and one path stored the unique ID where the base catalog ID was expected.

## Repair strategy

1. `getSessionGift()` now returns a generic catalog gift only for ordinary local gift records. For collectible records it returns `null` when the session object is unavailable, forcing the caller to reconstruct a `TL_starGiftUnique`.
2. The reconstruction path restores the unique ID, base catalog ID, title, slug, number, backdrop colors, pattern document, and image. If the animated document cache is cold, the base catalog gift is used only as an image source; the runtime object remains `TL_starGiftUnique`.
3. New purchases persist the pattern document ID and the actual base catalog `gift_id`, with compatibility for older preference records.
4. All fallback lookups are null-safe and do not mutate the restored object into an ordinary gift.

## Verification checklist

- Buy a collectible, force-stop the app, relaunch, and open the profile gift section.
- Confirm the card retains the collectible gradient/backdrop and number ribbon.
- Tap the card and confirm the unique detail page shows its title, number, attributes, and collectible information.
- Repeat after clearing only the in-memory process; do not delete app preferences during the test.
- Test an ordinary local gift to ensure it still uses the ordinary white card and generic catalog lookup.
- Test a collectible whose animated document cache is cold; confirm no crash/black screen and that the base document fallback is used only for the image.

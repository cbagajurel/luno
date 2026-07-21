export const GITHUB_URL = "https://github.com/cbagajurel/luno";

/**
 * Play Store listing. The id is the app's applicationId (`com.luno.gateway`) —
 * update this if the published listing uses a different one.
 */
export const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.luno.gateway";

/**
 * Play Console requires a reachable privacy policy, and a data-deletion URL on
 * the Data safety form. These are the values entered there — changing one means
 * updating the listing too. They are basePath-prefixed because the Play Console
 * fields need absolute paths, not router-relative ones.
 */
export const PRIVACY_URL = "/luno/legal/privacy";
export const TERMS_URL = "/luno/legal/terms";
export const DATA_DELETION_URL = "/luno/legal/data-deletion";

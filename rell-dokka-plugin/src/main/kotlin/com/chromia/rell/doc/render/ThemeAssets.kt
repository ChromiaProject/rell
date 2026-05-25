/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import org.intellij.lang.annotations.Language

/**
 * Inline SVG payload for the sun + moon icons that share one `<button class="theme-toggle">`.
 * CSS picks which `<svg>` is visible based on `[data-theme="dark"]`.
 */
internal const val THEME_TOGGLE_SVG: String = """
<svg class="icon-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
  <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
</svg>
<svg class="icon-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
  <circle cx="12" cy="12" r="4"/>
  <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/>
</svg>
"""

/**
 * Boot script for the theme. Runs in the document `<head>` *before* the page paints, so the
 * background colour doesn't briefly flash white when the user has chosen the dark theme. After
 * DOMContentLoaded it wires up the toggle button and persists the user's preference in
 * `localStorage` under the key `rell-docs-theme`.
 *
 * The script also follows the OS `prefers-color-scheme` setting when no manual override has
 * been saved yet.
 */
@Language("JavaScript")
internal const val THEME_BOOT_JS: String = """
(function() {
  const THEME_KEY = 'rell-docs-theme';
  const SCROLL_KEY = 'rell-docs-nav-scroll';
  function resolveTheme() {
    let stored = null;
    try { stored = localStorage.getItem(THEME_KEY); } catch (e) {}
    if (stored === 'dark' || stored === 'light') return stored;
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
    return 'light';
  }
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
  }
  applyTheme(resolveTheme());

  document.addEventListener('DOMContentLoaded', function() {
    const btn = document.querySelector('.theme-toggle');
    if (btn) {
      btn.addEventListener('click', function() {
        const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        applyTheme(next);
        try { localStorage.setItem(THEME_KEY, next); } catch (e) {}
      });
    }

    // Each page is its own document load, so the sidebar's scrollTop resets to 0 every
    // time a definition opens. We snapshot scrollTop to sessionStorage on link click +
    // pagehide, then restore on the next load. sessionStorage scope ≈ the doc-site tab,
    // which matches the natural "reading session" boundary.
    // Persist `<details open>` state for nav packages across page loads. Each `nav-pkg-block`
    // has a `.nav-pkg` link with an `anchor-label` carrying the package qname — we use that as
    // the stable identity. The Kotlin renderer already pre-opens the *current* package; this
    // augments it with whatever the user expanded on the previous page.
    const PKG_KEY = 'rell-docs-open-pkgs';
    const detailsBlocks = document.querySelectorAll('details.nav-pkg-block');
    if (detailsBlocks.length > 0) {
      let storedOpen = [];
      try {
        const raw = sessionStorage.getItem(PKG_KEY);
        if (raw) storedOpen = JSON.parse(raw);
      } catch (e) {}
      const openSet = new Set(storedOpen);
      detailsBlocks.forEach(function(block) {
        const labelEl = block.querySelector('.nav-pkg');
        const label = labelEl ? labelEl.getAttribute('anchor-label') : null;
        if (label && openSet.has(label)) block.open = true;
        block.addEventListener('toggle', function() {
          const link = block.querySelector('.nav-pkg');
          const lbl = link ? link.getAttribute('anchor-label') : null;
          if (!lbl) return;
          if (block.open) openSet.add(lbl); else openSet.delete(lbl);
          try { sessionStorage.setItem(PKG_KEY, JSON.stringify(Array.from(openSet))); } catch (e) {}
        });
      });
    }

    const sidebar = document.querySelector('.sidebar');
    if (sidebar) {
      let saved = null;
      try { saved = sessionStorage.getItem(SCROLL_KEY); } catch (e) {}
      if (saved !== null) {
        const n = parseInt(saved, 10);
        if (!isNaN(n)) sidebar.scrollTop = n;
      }
      function snapshot() {
        try { sessionStorage.setItem(SCROLL_KEY, String(sidebar.scrollTop)); } catch (e) {}
      }
      sidebar.addEventListener('click', function(e) {
        if (e.target.closest('a')) snapshot();
      });
      window.addEventListener('pagehide', snapshot);
    }
  });
})();
"""

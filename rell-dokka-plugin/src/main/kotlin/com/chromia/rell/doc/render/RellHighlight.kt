/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import org.intellij.lang.annotations.Language

/**
 * Inline Rell syntax highlighter. The keyword list is derived from `rell-base/frontend/src/
 * main/antlr/Rell.g4` (any single-quoted string-literal alternative in a parser rule). The
 * built-in type list is the subset of stdlib types that show up in 90% of doc snippets.
 *
 * Runs in the document `<head>` so it can pre-tokenize before paint — no flash of unstyled
 * code. Looks for `<pre><code class="language-rell">` blocks, which is what commonmark emits
 * for ```rell fences.
 *
 * The tokenizer is a single regex with alternation: comment | string | bytes-literal |
 * number | annotation | keyword | builtin-type. Anything else is passed through verbatim
 * (escaped, since we set innerHTML).
 */
@Language("JavaScript")
internal const val RELL_HIGHLIGHT_JS: String = """
(function() {
  // Derived from Rell.g4: every single-quoted string-literal alternative on a parser rule.
  const KEYWORDS = new Set([
    'module','abstract','mutable','override',
    'entity','class','key','index','object','struct','record','enum','function','namespace',
    'import','operation','query','include','val','var',
    'virtual',
    'if','else','when','while','for','in','break','continue','return',
    'update','delete','guard',
    'create',
    'not','and','or',
    'limit','offset',
  ]);
  const LITERALS = new Set(['true','false','null']);
  // Common stdlib types — used as `tokenType=type` colouring when they appear bare.
  const TYPES = new Set([
    'boolean','integer','big_integer','decimal','text','byte_array','rowid','timestamp',
    'pubkey','name','json','unit','range','gtv',
    'list','set','map','tuple','collection','iterable',
    'virtual_collection','virtual_list','virtual_map','virtual_set',
    'immutable','immutable_mirror_struct',
    'GTXValue','gtx_operation','gtx_transaction','gtx_transaction_body',
  ]);

  // Match order matters — earlier alternatives win, so comments and strings consume their
  // own internal punctuation before later rules see it.
  const TOKEN = new RegExp(
    [
      '(\\/\\*[\\s\\S]*?\\*\\/)',                       // 1: block comment
      '(\\/\\/[^\\n]*)',                                // 2: line comment
      '(x"[0-9A-Fa-f]*"|x\'[0-9A-Fa-f]*\')',            // 3: bytes literal
      '("(?:\\\\.|[^"\\\\])*"|\'(?:\\\\.|[^\'\\\\])*\')', // 4: string
      '(0x[0-9A-Fa-f]+L?|\\d+\\.\\d+(?:[eE][+-]?\\d+)?|\\d+(?:[eE][+-]?\\d+)?L?)', // 5: number
      '(@[A-Za-z_][A-Za-z0-9_]*)',                      // 6: annotation
      '(->)',                                            // 7: arrow (lambda / when / fn-type)
      '([A-Za-z_][A-Za-z0-9_]*)',                       // 8: identifier (k/v lookup)
    ].join('|'),
    'g'
  );

  function escape(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function highlight(src) {
    let out = '';
    let last = 0;
    let m;
    TOKEN.lastIndex = 0;
    while ((m = TOKEN.exec(src)) !== null) {
      if (m.index > last) out += escape(src.slice(last, m.index));
      let cls = null;
      let text = m[0];
      if (m[1] || m[2])      cls = 'rhl-comment';
      else if (m[3])         cls = 'rhl-bytes';
      else if (m[4])         cls = 'rhl-str';
      else if (m[5])         cls = 'rhl-num';
      else if (m[6])         cls = 'rhl-anno';
      else if (m[7])         cls = 'rhl-op';
      else if (m[8]) {
        const w = m[8];
        if (KEYWORDS.has(w))      cls = 'rhl-kw';
        else if (LITERALS.has(w)) cls = 'rhl-lit';
        else if (TYPES.has(w))    cls = 'rhl-type';
      }
      out += cls ? '<span class="' + cls + '">' + escape(text) + '</span>' : escape(text);
      last = m.index + text.length;
    }
    if (last < src.length) out += escape(src.slice(last));
    return out;
  }

  // ── JavaScript highlighter ──────────────────────────────────────────────
  const JS_KEYWORDS = new Set([
    'var','let','const','function','return','if','else','while','for','do',
    'new','delete','typeof','instanceof','in','of','void',
    'class','extends','super','this','static','get','set',
    'import','export','from','as','default',
    'try','catch','finally','throw','switch','case','break','continue',
    'async','await','yield','debugger',
  ]);
  const JS_LITERALS = new Set(['true','false','null','undefined','NaN','Infinity']);
  const JS_TOKEN = new RegExp(
    [
      '(\\/\\*[\\s\\S]*?\\*\\/)',                         // 1: block comment
      '(\\/\\/[^\\n]*)',                                  // 2: line comment
      '(`(?:\\\\.|[^`\\\\])*`)',                          // 3: template literal
      '("(?:\\\\.|[^"\\\\])*"|\'(?:\\\\.|[^\'\\\\])*\')', // 4: string
      '(0[xX][0-9A-Fa-f]+n?|0[bB][01]+n?|0[oO][0-7]+n?|\\d+\\.\\d*(?:[eE][+-]?\\d+)?|\\d+(?:[eE][+-]?\\d+)?n?)', // 5: number
      '([A-Za-z_$][A-Za-z0-9_$]*)',                      // 6: identifier
    ].join('|'),
    'g'
  );

  function highlightJs(src) {
    let out = '';
    let last = 0;
    let m;
    JS_TOKEN.lastIndex = 0;
    while ((m = JS_TOKEN.exec(src)) !== null) {
      if (m.index > last) out += escape(src.slice(last, m.index));
      let cls = null;
      const text = m[0];
      if (m[1] || m[2])      cls = 'rhl-comment';
      else if (m[3] || m[4]) cls = 'rhl-str';
      else if (m[5])         cls = 'rhl-num';
      else if (m[6]) {
        const w = m[6];
        if (JS_KEYWORDS.has(w))      cls = 'rhl-kw';
        else if (JS_LITERALS.has(w)) cls = 'rhl-lit';
      }
      out += cls ? '<span class="' + cls + '">' + escape(text) + '</span>' : escape(text);
      last = m.index + text.length;
    }
    if (last < src.length) out += escape(src.slice(last));
    return out;
  }

  function highlightAll() {
    document.querySelectorAll('pre > code.language-rell').forEach(function(code) {
      if (code.dataset.rhlDone === '1') return;
      const src = code.textContent || '';
      code.innerHTML = highlight(src);
      code.dataset.rhlDone = '1';
    });
    document.querySelectorAll('pre > code.language-javascript, pre > code.language-js').forEach(function(code) {
      if (code.dataset.rhlDone === '1') return;
      const src = code.textContent || '';
      code.innerHTML = highlightJs(src);
      code.dataset.rhlDone = '1';
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', highlightAll);
  } else {
    highlightAll();
  }
})();
"""

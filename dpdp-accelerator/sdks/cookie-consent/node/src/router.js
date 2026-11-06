/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

'use strict';

const fs = require('fs');
const path = require('path');
const express = require('express');
const { readSubjectCookie, writeSubjectCookie } = require('./subjectCookie');
const { StaleRevisionError, DecisionNotSavedError } = require('./cookieConsentService');

// The loader is shared by every cookie consent SDK, so it lives beside this one in the repository;
// prepack copies it into the package, where it is found first.
const BROWSER_DIR = [path.join(__dirname, '..', 'browser'), path.join(__dirname, '..', '..', 'browser')]
  .find((dir) => fs.existsSync(path.join(dir, 'loader.js')));
const LIBRARY_DIST = path.join(path.dirname(require.resolve('vanilla-cookieconsent/package.json')), 'dist');
// Only what the loader requests: the package's other builds are not part of the public surface.
const LIBRARY_FILES = ['cookieconsent.umd.js', 'cookieconsent.css'];

function loaderSource() {
  return ['loader-core.js', 'loader.js']
    .map((file) => fs.readFileSync(path.join(BROWSER_DIR, file), 'utf8'))
    .join('\n');
}

function isValidDecision(body) {
  if (!body || !Number.isInteger(body.revision)) return false;
  const { categories } = body;
  if (!categories || typeof categories !== 'object' || Array.isArray(categories)) return false;
  return Object.values(categories).every((value) => typeof value === 'boolean');
}

function createRouter({ service, cookieSecret, cookieOptions, getUsername, onError }) {
  const router = express.Router();
  const loader = loaderSource();

  router.get('/state', async (req, res) => {
    res.set('Cache-Control', 'no-store');
    try {
      res.json(await service.getState({ username: await getUsername(req), cookie: readSubjectCookie(req, cookieSecret) }));
    } catch (e) {
      onError(e);
      res.status(502).json({ error: 'Cookie consent state is unavailable' });
    }
  });

  router.post('/decision', express.json({ limit: '4kb' }), async (req, res) => {
    res.set('Cache-Control', 'no-store');
    if (!isValidDecision(req.body)) {
      res.status(400).json({ error: 'Expected {revision: integer, categories: {name: boolean}}' });
      return;
    }
    try {
      const { cookie, state } = await service.saveDecision({
        username: await getUsername(req),
        cookie: readSubjectCookie(req, cookieSecret),
        categories: req.body.categories,
        revision: req.body.revision,
      });
      writeSubjectCookie(res, cookieSecret, cookie, cookieOptions);
      res.json(state);
    } catch (e) {
      if (e instanceof StaleRevisionError) {
        res.status(409).json({ error: e.message });
        return;
      }
      if (e instanceof DecisionNotSavedError) {
        writeSubjectCookie(res, cookieSecret, e.cookie, cookieOptions);
        onError(e.cause);
      } else {
        onError(e);
      }
      res.status(502).json({ error: 'Cookie consent could not be saved' });
    }
  });

  router.get('/loader.js', (req, res) => {
    res.type('application/javascript').set('Cache-Control', 'no-cache').send(loader);
  });

  for (const file of LIBRARY_FILES) {
    const body = fs.readFileSync(path.join(LIBRARY_DIST, file));
    router.get(`/vendor/${file}`, (req, res) => {
      res.type(path.extname(file)).set('Cache-Control', 'public, max-age=86400').send(body);
    });
  }

  return router;
}

module.exports = { createRouter };

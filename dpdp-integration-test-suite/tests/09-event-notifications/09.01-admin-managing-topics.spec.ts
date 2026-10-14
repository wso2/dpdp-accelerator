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

import { loginAsConsentAdmin, test, expect } from '../../fixtures/auth.fixtures'
import { TopicDeleteDialog } from '../../pages/TopicDeleteDialog'
import { TopicRegisterDialog } from '../../pages/TopicRegisterDialog'
import { TopicsPage } from '../../pages/TopicsPage'
import { seedActiveTopicViaApi } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * Registering and deregistering Event Notification topics through the portal UI
 * (TopicsPage.tsx/TopicRegisterDialog.tsx/TopicDeleteDialog.tsx) and TopicServiceImpl's
 * server-side rules. See AGENTS.md for setup and known drift -
 * notably: creating a topic shows NO literal "success message" toast (useCreateTopicMutation has
 * no onSuccess snackbar); the dialog closing and the row appearing are the only observable
 * success signals.
 */
test.describe('Admin managing Topics', () => {
  test.describe('Creating Topics', () => {
    test('09.01.01 - Creates a user topic through the Register Topic dialog', async ({ browser }) => {
      const page = await loginAsConsentAdmin(browser)
      try {
        const topicsPage = new TopicsPage(page)
        await topicsPage.goto()
        await topicsPage.openRegisterDialog()

        const dialog = new TopicRegisterDialog(page)
        const name = uniqueMarker('consent-status-changed')
        await dialog.register(name, 'Changes to consent status.')

        await expect(dialog.root).toBeHidden()
        await topicsPage.search(name)
        const row = topicsPage.rowByName(name)
        await expect(row).toBeVisible()
        await expect(row).toContainText('Active')
        await expect(row).toContainText('Changes to consent status.')
      } finally {
        await page.context().close()
      }
    })

    test('09.01.02 - Leaving the topic name empty shows the required-field error and blocks submission', async ({
      browser,
    }) => {
      const page = await loginAsConsentAdmin(browser)
      try {
        const topicsPage = new TopicsPage(page)
        await topicsPage.goto()
        await topicsPage.openRegisterDialog()

        const dialog = new TopicRegisterDialog(page)
        await dialog.fillDescription('Optional text with no name.')
        await dialog.submit()

        // TopicRegisterDialog.tsx's <form> has no `noValidate`, and the Name field carries the
        // native HTML `required` attribute (TextField's own `required` prop) - the browser's
        // constraint validation blocks the submit event before it ever reaches React, so the
        // component's own custom "Topic name is required." helper-text branch is unreachable
        // through a real click (the browser's constraint validation blocks the submit event).
        // The two are functionally equivalent from a user's perspective (submission is blocked,
        // a required-field message is shown), so this asserts the actual, observable outcome -
        // native constraint validation failing - rather than the effectively dead custom message.
        // Duck-typed rather than cast to HTMLInputElement - this suite's tsconfig has no "dom" lib.
        const nameFieldValid = await dialog.nameField.evaluate(
          (el: { validity: { valid: boolean } }) => el.validity.valid,
        )
        expect(nameFieldValid).toBe(false)
        await expect(dialog.root).toBeVisible()
      } finally {
        await page.context().close()
      }
    })

    test('09.01.03 - Creating a topic whose name already exists is rejected case-insensitively', async ({
      browser,
      consentAdminEventApi,
    }) => {
      const page = await loginAsConsentAdmin(browser)
      try {
        const existing = await seedActiveTopicViaApi(consentAdminEventApi, 'case-collide')

        const topicsPage = new TopicsPage(page)
        await topicsPage.goto()
        await topicsPage.openRegisterDialog()

        const dialog = new TopicRegisterDialog(page)
        await dialog.register(existing.name.toUpperCase())

        // The dialog stays open with the server's exact description text
        // (TopicServiceImpl.TOPIC_ALREADY_EXISTS_ERROR_MSG, surfaced verbatim by apiClient.ts's
        // payloadToError, which prefers the error body's `description` field).
        await expect(dialog.root).toBeVisible()
        await expect(
          dialog.root.getByText('A topic with the specified name already exists for this organization.'),
        ).toBeVisible()

        const listResponse = await consentAdminEventApi.listTopics({ search: existing.name, status: 'ACTIVE' })
        const { items } = (await listResponse.json()) as { items: { name: string }[] }
        expect(items.filter((t) => t.name.toLowerCase() === existing.name.toLowerCase())).toHaveLength(1)
      } finally {
        await page.context().close()
      }
    })

    test('09.01.04 - Topic input is trimmed before persistence', async ({ browser, consentAdminEventApi }) => {
      const page = await loginAsConsentAdmin(browser)
      try {
        const topicsPage = new TopicsPage(page)
        await topicsPage.goto()
        await topicsPage.openRegisterDialog()

        const dialog = new TopicRegisterDialog(page)
        const name = uniqueMarker('data-erasure-requested')
        await dialog.register(`  ${name}  `, '  Erasure request event.  ')
        await expect(dialog.root).toBeHidden()

        const listResponse = await consentAdminEventApi.listTopics({ search: name })
        const { items } = (await listResponse.json()) as { items: { name: string; description?: string }[] }
        const created = items.find((t) => t.name === name)
        expect(created, `expected an exact (untrimmed-surrounding-whitespace) match for "${name}"`).toBeDefined()
        expect(created?.description).toBe('Erasure request event.')
      } finally {
        await page.context().close()
      }
    })
  })

  test.describe('Deleting Topics', () => {
    test('09.01.05 - Deletes a user-created topic with no active subscriptions', async ({
      browser,
      consentAdminEventApi,
    }) => {
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'delete-me')
      const page = await loginAsConsentAdmin(browser)
      try {
        const topicsPage = new TopicsPage(page)
        await topicsPage.goto()
        await topicsPage.search(topic.name)
        await topicsPage.deleteTopicByName(topic.name)
        await new TopicDeleteDialog(page).confirm()

        await topicsPage.search(topic.name)
        await topicsPage.filterByStatus('Deleted')
        await expect(topicsPage.rowByName(topic.name)).toBeVisible()

        // The audit row remains readable by id after deletion.
        const getResponse = await consentAdminEventApi.listTopics({ search: topic.name, status: 'DELETED' })
        const { items } = (await getResponse.json()) as { items: { topicId: string; status: string }[] }
        expect(items.some((t) => t.topicId === topic.topicId && t.status.toUpperCase() === 'DELETED')).toBe(
          true,
        )
      } finally {
        await page.context().close()
      }
    })
  })
})

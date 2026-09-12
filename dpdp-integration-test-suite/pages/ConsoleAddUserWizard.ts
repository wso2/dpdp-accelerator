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

import { type Locator, type Page } from '@playwright/test'

export interface NewUserFields {
  username: string
  email: string
  firstName: string
  lastName: string
  password: string
}

/**
 * The three-step "Create User" wizard on a tenant's own Console, at User Management > Users >
 * "Add User" > "Single User". Confirmed live, end to end, that this succeeds
 * (`POST .../scim2/Users` -> 201) where the identical call replayed directly via curl (Basic auth
 * or a manually-attached Bearer token) 401s every time - Console's own frontend authenticates its
 * internal calls some other way a standalone `Authorization` header replay doesn't reproduce.
 * This wizard is therefore the only way this suite creates a second tenant user.
 *
 * The "Last Name" field is easy to miss: it renders with no visible error until Next is clicked,
 * at which point the wizard just silently fails to advance (no exception, no visible message
 * without scrolling) - confirmed empirically, cost real debugging time.
 */
export class ConsoleAddUserWizard {
  readonly addUserButton: Locator
  readonly singleUserOption: Locator
  readonly root: Locator
  readonly usernameField: Locator
  readonly emailField: Locator
  readonly firstNameField: Locator
  readonly lastNameField: Locator
  readonly setPasswordOption: Locator
  readonly passwordField: Locator
  readonly nextButton: Locator
  readonly saveAndContinueButton: Locator
  readonly closeButton: Locator

  // Not stored - every locator this class needs is built from it right here in the constructor.
  constructor(page: Page) {
    this.addUserButton = page.getByRole('button', { name: 'Add User' })
    this.singleUserOption = page.getByText('Single User', { exact: true })
    // Unscoped, unlike the rest of this suite's page objects: the wizard carries neither
    // `role="dialog"` nor the Semantic UI `.ui.modal` class it used to, so there is no stable
    // container to hang the fields off. Its placeholders are unique on the Users page while it
    // is open, which is what makes page-level locators safe here.
    this.root = page.getByText('Follow the steps to create a new user.')
    // One field, not two: Console now labels it "Username (Email)" and the accelerator
    // enforces an email-address username, so the separate username input is gone.
    this.usernameField = page.getByPlaceholder('Enter the email address')
    this.emailField = this.usernameField
    this.firstNameField = page.getByPlaceholder('Enter the first name')
    this.lastNameField = page.getByPlaceholder('Enter the last name')
    this.setPasswordOption = page.getByText('Set a password for the user', { exact: true })
    this.passwordField = page.getByPlaceholder('Enter the password')
    // exact: the Users table's pagination has a "Next Page" button that a substring match
    // also resolves to.
    this.nextButton = page.getByRole('button', { name: 'Next', exact: true })
    this.saveAndContinueButton = page.getByRole('button', { name: 'Save & Continue' })
    this.closeButton = page.getByRole('button', { name: 'Close', exact: true })
  }

  async open(): Promise<void> {
    // "Add User" opens a dropdown whose items stay mounted while hidden, so a first click that
    // doesn't register (the button renders before Console attaches its handler, which is easy to
    // lose when several workers drive Console at once) leaves singleUserOption resolvable but
    // permanently invisible. Waiting cannot recover from that - the menu never opens without
    // another click - and the blind second click then stalls until the worker fixture's own
    // 240s timeout, surfacing as "fixture timeout during setup" rather than a clear failure.
    // Retry the opening gesture instead.
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      await this.addUserButton.click()
      try {
        await this.singleUserOption.waitFor({ state: 'visible', timeout: 5_000 })
        break
      } catch {
        if (attempt === 3) {
          throw new Error('Console\'s "Add User" menu did not open after 3 attempts.')
        }
      }
    }
    await this.singleUserOption.click()
  }

  /** Fills every Basic Details field, including the two easy-to-miss ones: Last Name and the
   * explicit-password option (the wizard defaults to emailing an invitation instead). */
  async fillBasicDetails(fields: NewUserFields): Promise<void> {
    // username and email are the same field now; fields.email is kept in the interface so
    // callers need not change, but filling it twice would just overwrite the same input.
    await this.usernameField.fill(fields.username)
    await this.firstNameField.fill(fields.firstName)
    await this.lastNameField.fill(fields.lastName)
    await this.setPasswordOption.click()
    await this.passwordField.fill(fields.password)
  }

  /** Basic Details -> User Groups. The existing "admin" group needs no action; this suite never
   * assigns this user to it. */
  async continueToGroups(): Promise<void> {
    await this.nextButton.click()
  }

  /** User Groups -> Invitation. Confirmed live: this is the step whose response is the actual
   * `POST .../scim2/Users` call - by the time this resolves, the user exists. */
  async continueToInvitation(): Promise<void> {
    await this.saveAndContinueButton.click()
  }

  /** Dismisses the final step, which shows the password/invitation text one last time. */
  async finish(): Promise<void> {
    await this.closeButton.click()
  }

  /** The full wizard, start to finish, for the common case of just wanting the user created. */
  async createUser(fields: NewUserFields): Promise<void> {
    await this.open()
    await this.fillBasicDetails(fields)
    await this.continueToGroups()
    await this.continueToInvitation()
    await this.finish()
  }
}

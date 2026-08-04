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

import { AppShell, Box, ColorSchemeToggle, Header } from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router-dom'
import AppSidebar from '../sidebar/AppSidebar'
import RouteScrollManager from './RouteScrollManager'
import UserProfileMenu from './UserProfileMenu'

function MainLayout(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState<boolean>(false)

  return (
    <AppShell collapseOnSelectOnMobile>
      <AppShell.Navbar>
        <Header>
          <Header.Toggle
            collapsed={isSidebarCollapsed}
            onToggle={() => {
              setIsSidebarCollapsed((previous) => !previous)
            }}
          />
          <Header.Brand>
            <Header.BrandLogo>
              <Box
                component="img"
                src="/wso2-logo.png"
                alt=""
                aria-hidden="true"
                sx={{
                  width: 28,
                  height: 28,
                  objectFit: 'contain',
                }}
              />
            </Header.BrandLogo>
            <Header.BrandTitle>{t('app.title')}</Header.BrandTitle>
          </Header.Brand>
          <Header.Spacer />
          <Header.Actions>
            <ColorSchemeToggle />
            <UserProfileMenu />
          </Header.Actions>
        </Header>
      </AppShell.Navbar>

      <AppShell.Sidebar>
        <AppSidebar collapsed={isSidebarCollapsed} />
      </AppShell.Sidebar>

      <AppShell.Main>
        <RouteScrollManager />
        <Box
          sx={{
            width: '100%',
            maxWidth: 'none',
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <Outlet />
        </Box>
      </AppShell.Main>
    </AppShell>
  )
}

export default MainLayout

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

import { Sidebar } from '@wso2/oxygen-ui'
import {
  Blocks,
  Clock3,
  House,
  ShieldCheck,
  ShieldPlus,
  Target,
  UserCog,
  Users,
} from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'
import useAuthorization from '../../../features/auth/useAuthorization'
import { PORTAL_SCOPES, type PortalScope } from '../../../utils/portalScopes'
import { useActingAs } from '../../../features/nominee/actingAs/actingAsContext'

interface AppSidebarProps {
  collapsed: boolean
}

interface SidebarItem {
  id: string
  labelKey: string
  path: string
  icon: React.JSX.Element
  requiredScope: PortalScope
}

const DASHBOARD_ITEMS: SidebarItem[] = [
  {
    id: 'dashboard',
    labelKey: 'sidebar.dashboard',
    path: '/dashboard',
    icon: <House size={18} />,
    requiredScope: PORTAL_SCOPES.CONSENTS_READ_SELF,
  },
]

const CONSENT_ITEMS: SidebarItem[] = [
  {
    id: 'all-consents',
    labelKey: 'sidebar.allConsents',
    path: '/consents',
    icon: <ShieldCheck size={18} />,
    requiredScope: PORTAL_SCOPES.CONSENTS_READ_SELF,
  },
  {
    id: 'pending-consents',
    labelKey: 'sidebar.pendingConsents',
    path: '/consents?status=Pending',
    icon: <Clock3 size={18} />,
    requiredScope: PORTAL_SCOPES.CONSENTS_READ_SELF,
  },
]

const CATALOG_ITEMS: SidebarItem[] = [
  {
    id: 'purposes',
    labelKey: 'sidebar.purposes',
    path: '/purposes',
    icon: <Target size={18} />,
    requiredScope: PORTAL_SCOPES.PURPOSES_READ,
  },
  {
    id: 'elements',
    labelKey: 'sidebar.elements',
    path: '/elements',
    icon: <Blocks size={18} />,
    requiredScope: PORTAL_SCOPES.ELEMENTS_READ,
  },
]

const ADMINISTRATION_ITEMS: SidebarItem[] = [
  {
    id: 'administration-consents',
    labelKey: 'sidebar.adminConsents',
    path: '/administration/consents',
    icon: <ShieldPlus size={18} />,
    requiredScope: PORTAL_SCOPES.CONSENTS_READ_ANY,
  },
  {
    id: 'administration-nominees',
    labelKey: 'sidebar.nomineeActivation',
    path: '/administration/nominees',
    icon: <UserCog size={18} />,
    requiredScope: PORTAL_SCOPES.PROFILE_READ_ANY,
  },
]

const NOMINEE_ITEMS: SidebarItem[] = [
  {
    id: 'nominations',
    labelKey: 'sidebar.nominations',
    path: '/nominations',
    icon: <Users size={18} />,
    requiredScope: PORTAL_SCOPES.PROFILE_READ_SELF,
  },
]

function mapPathToMenuId(pathname: string, search: string): string {
  if (pathname.startsWith('/administration/consents')) {
    return 'administration-consents'
  }

  if (pathname.startsWith('/administration/nominees')) {
    return 'administration-nominees'
  }

  if (pathname.startsWith('/nominations')) {
    return 'nominations'
  }

  if (pathname.startsWith('/dashboard')) {
    return 'dashboard'
  }

  if (pathname.startsWith('/consents')) {
    const status = new URLSearchParams(search).get('status')

    if (status === 'Pending') {
      return 'pending-consents'
    }

    return 'all-consents'
  }

  if (pathname.startsWith('/purposes')) {
    return 'purposes'
  }

  if (pathname.startsWith('/elements')) {
    return 'elements'
  }

  return 'dashboard'
}

function AppSidebar({ collapsed }: AppSidebarProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const location = useLocation()
  const { hasScope } = useAuthorization()
  const { session } = useActingAs()

  const dashboardItems = DASHBOARD_ITEMS.filter((item) => hasScope(item.requiredScope))
  const consentItems = CONSENT_ITEMS.filter((item) => hasScope(item.requiredScope))
  const catalogItems = CATALOG_ITEMS.filter((item) => hasScope(item.requiredScope))
  const administrationItems = ADMINISTRATION_ITEMS.filter((item) => hasScope(item.requiredScope))
  // Withheld while acting for someone else: a nominee exercises the owner's data
  // rights, never their ability to appoint further nominees. Who else sees this
  // is decided by the scopes their role carries, as with every other item here.
  const nomineeItems = session ? [] : NOMINEE_ITEMS.filter((item) => hasScope(item.requiredScope))
  const visibleItems = [
    ...dashboardItems,
    ...consentItems,
    ...catalogItems,
    ...nomineeItems,
    ...administrationItems,
  ]

  const activeItem = mapPathToMenuId(location.pathname, location.search)

  return (
    <Sidebar
      collapsed={collapsed}
      activeItem={activeItem}
      onSelect={(id) => {
        const selectedItem = visibleItems.find((item) => item.id === id)

        if (selectedItem) {
          navigate(selectedItem.path)
        }
      }}
      aria-label={t('sidebar.ariaLabel')}
    >
      <Sidebar.Nav>
        {dashboardItems.length > 0 ? (
          <Sidebar.Category>
            {dashboardItems.map((item) => (
              <Sidebar.Item key={item.id} id={item.id}>
                <Sidebar.ItemIcon>{item.icon}</Sidebar.ItemIcon>
                <Sidebar.ItemLabel>{t(item.labelKey)}</Sidebar.ItemLabel>
              </Sidebar.Item>
            ))}
          </Sidebar.Category>
        ) : null}

        {nomineeItems.length > 0 ? (
          <Sidebar.Category>
            {nomineeItems.map((item) => (
              <Sidebar.Item key={item.id} id={item.id}>
                <Sidebar.ItemIcon>{item.icon}</Sidebar.ItemIcon>
                <Sidebar.ItemLabel>{t(item.labelKey)}</Sidebar.ItemLabel>
              </Sidebar.Item>
            ))}
          </Sidebar.Category>
        ) : null}

        {consentItems.length > 0 ? (
          <Sidebar.Category>
            <Sidebar.CategoryLabel>{t('sidebar.consent')}</Sidebar.CategoryLabel>
            {consentItems.map((item) => (
              <Sidebar.Item key={item.id} id={item.id}>
                <Sidebar.ItemIcon>{item.icon}</Sidebar.ItemIcon>
                <Sidebar.ItemLabel>{t(item.labelKey)}</Sidebar.ItemLabel>
              </Sidebar.Item>
            ))}
          </Sidebar.Category>
        ) : null}

        {administrationItems.length > 0 ? (
          <Sidebar.Category>
            <Sidebar.CategoryLabel>{t('sidebar.administration')}</Sidebar.CategoryLabel>
            {administrationItems.map((item) => (
              <Sidebar.Item key={item.id} id={item.id}>
                <Sidebar.ItemIcon>{item.icon}</Sidebar.ItemIcon>
                <Sidebar.ItemLabel>{t(item.labelKey)}</Sidebar.ItemLabel>
              </Sidebar.Item>
            ))}
          </Sidebar.Category>
        ) : null}

        {catalogItems.length > 0 ? (
          <Sidebar.Category>
            <Sidebar.CategoryLabel>{t('sidebar.catalog')}</Sidebar.CategoryLabel>
            {catalogItems.map((item) => (
              <Sidebar.Item key={item.id} id={item.id}>
                <Sidebar.ItemIcon>{item.icon}</Sidebar.ItemIcon>
                <Sidebar.ItemLabel>{t(item.labelKey)}</Sidebar.ItemLabel>
              </Sidebar.Item>
            ))}
          </Sidebar.Category>
        ) : null}
      </Sidebar.Nav>
    </Sidebar>
  )
}

export default AppSidebar

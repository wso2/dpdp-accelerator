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

import { Chip, Typography } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import SubscriptionChipListSection from './SubscriptionChipListSection'
import type { PurposeFilterMode } from '../../../types/subscription'

interface Props {
  purposes: string[]
  filterType: PurposeFilterMode
}

export default function SubscriptionPurposesSection({
  purposes,
  filterType,
}: Props): React.JSX.Element {
  const { t } = useTranslation('common')

  const filterLabel = t(`subscriptions.filterType.${filterType}`, filterType)

  return (
    <SubscriptionChipListSection
      title={t('subscriptions.details.purposesTitle')}
      count={purposes.length}
      searchLabel={t('subscriptions.details.searchPurposes')}
      noMatchesLabel={t('subscriptions.details.noPurposes')}
      items={purposes}
      collapsible
      contentId="subscribed-purposes-content"
      headerExtra={<Chip size="small" variant="outlined" label={filterLabel} />}
      emptyNotice={
        filterType === 'all' ? (
          <Typography variant="body2" color="text.secondary">
            {t('subscriptions.details.allPurposesNotice')}
          </Typography>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {t('subscriptions.details.noPurposes')}
          </Typography>
        )
      }
    />
  )
}

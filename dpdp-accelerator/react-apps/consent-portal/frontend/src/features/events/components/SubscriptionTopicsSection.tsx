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

import { useTranslation } from 'react-i18next'
import SubscriptionChipListSection from './SubscriptionChipListSection'

interface Props {
  topics: string[]
}

export default function SubscriptionTopicsSection({ topics }: Props): React.JSX.Element {
  const { t } = useTranslation('common')

  const title = t('subscriptions.topicUi.subscribedCount', { count: topics.length }).replace(
    /\s*\([^)]*\)\s*$/,
    '',
  )

  return (
    <SubscriptionChipListSection
      title={title}
      count={topics.length}
      searchLabel={t('subscriptions.topicUi.searchAssociated')}
      noMatchesLabel={t('subscriptions.topicUi.noMatches')}
      items={topics}
      collapsible={false}
      contentId="subscribed-topic-content"
    />
  )
}

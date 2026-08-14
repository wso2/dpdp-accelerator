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

import { Chip } from '@wso2/oxygen-ui'
import type { ElementType } from '../../../types/catalog'
import { ELEMENT_TYPE_PRESENTATION } from '../utils/elementTypePresentation'

interface ElementTypeChipProps {
  type: ElementType
}

function ElementTypeChip({ type }: ElementTypeChipProps): React.JSX.Element {
  const { Icon, label } = ELEMENT_TYPE_PRESENTATION[type]

  return <Chip size="small" sx={{ px: 0.5 }} icon={<Icon size={14} />} label={label} />
}

export default ElementTypeChip

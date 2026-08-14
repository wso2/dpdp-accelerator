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

import type { SxProps, Theme } from '@wso2/oxygen-ui'

/**
 * Padding for a DialogContent that follows a DialogTitle.
 *
 * Writing `pt` directly there does nothing. The component ships a rule
 * of its own:
 *
 * .MuiDialogTitle-root + .MuiDialogContent-root { padding-top: 0 }
 *
 * which is a two-class selector and therefore outranks the single class an
 * `sx` prop generates, whatever the order. The declaration is dropped in
 * silence.
 *
 * It matters because DialogContent scrolls. With no top padding the first
 * control sits flush against the scroll edge, and an outlined field's label -
 * which is drawn slightly above the input's border - is clipped by it.
 *
 * The doubled `&&` selector matches the same element twice, matching
 * that specificity, and later injection settles it.
 */
export function dialogContentPadding(top: number, horizontal = 3, bottom = 3): SxProps<Theme> {
  return { px: horizontal, pb: bottom, '&&': { pt: top } }
}

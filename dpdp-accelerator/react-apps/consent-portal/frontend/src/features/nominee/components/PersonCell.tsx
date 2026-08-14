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

import { Avatar, Skeleton, Stack, Typography } from '@wso2/oxygen-ui'
import { useUserDisplayQuery } from '../hooks/useNomineeQueries'

interface PersonCellProps {
  userId: string
  /** Shown underneath the name, and used as the fallback name when the lookup has none. Pass '' to omit. */
  email: string
}

/**
 * Initials for the avatar. Prefers the first letters of the first and last word
 * of a display name; falls back to the leading characters of the email local
 * part so an unresolved row still shows something meaningful rather than "@G".
 */
function initialsOf(name: string): string {
  const words = name
    .trim()
    .split(/\s+/)
    .filter((word) => word.length > 0)

  if (words.length === 0) {
    return '?'
  }

  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase()
  }

  return `${words[0][0]}${words[words.length - 1][0]}`.toUpperCase()
}

/**
 * Renders a person the way the WSO2 consoles do: avatar, display name as the
 * primary line, address underneath. The name is resolved per row, so this has
 * to be a component rather than a helper - a hook cannot run inside a map.
 */
function PersonCell({ userId, email }: PersonCellProps): React.JSX.Element {
  const query = useUserDisplayQuery(userId)
  const emailLocalPart = email ? email.split('@')[0] : ''
  const displayName = query.data ?? (emailLocalPart || userId)

  return (
    <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: 0 }}>
      <Avatar
        sx={{
          width: 36,
          height: 36,
          fontSize: 13,
          fontWeight: 700,
          bgcolor: 'primary.main',
        }}
      >
        {initialsOf(displayName)}
      </Avatar>
      <Stack spacing={0.25} sx={{ minWidth: 0 }}>
        {query.isLoading ? (
          <Skeleton variant="text" width={140} />
        ) : (
          <Typography variant="body2" fontWeight={600} noWrap>
            {displayName}
          </Typography>
        )}
        {email ? (
          <Typography variant="caption" color="text.secondary" noWrap>
            {email}
          </Typography>
        ) : null}
      </Stack>
    </Stack>
  )
}

export default PersonCell

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

import { Box, Stack, Typography } from '@wso2/oxygen-ui'

export interface DetailField {
  icon: React.ReactNode
  label: string
  value: React.ReactNode
}

interface DetailGridProps {
  fields: DetailField[]
}

function DetailGrid({ fields }: DetailGridProps): React.JSX.Element {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
        gap: { xs: 2, md: 3 },
      }}
    >
      {fields.map((field) => (
        <Stack key={field.label} spacing={0} minWidth={0}>
          <Typography
            variant="caption"
            color="text.secondary"
            fontWeight={700}
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 0.5,
              mb: 1,
              textTransform: 'uppercase',
              letterSpacing: 0.5,
            }}
          >
            {field.icon}
            {field.label}
          </Typography>
          <Typography
            component="div"
            variant="body2"
            fontWeight={500}
            sx={{ overflowWrap: 'anywhere' }}
          >
            {field.value || '-'}
          </Typography>
        </Stack>
      ))}
    </Box>
  )
}

export default DetailGrid

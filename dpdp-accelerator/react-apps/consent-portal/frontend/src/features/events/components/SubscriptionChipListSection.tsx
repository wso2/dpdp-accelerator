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

import {
  Card,
  CardContent,
  CardHeader,
  Button,
  Chip,
  Collapse,
  Divider,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronDown } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'

export interface SubscriptionChipListSectionProps {
  title: string
  count: number
  searchLabel: string
  noMatchesLabel: string
  items: string[]
  collapsible?: boolean
  initialExpanded?: boolean
  contentId?: string
  headerExtra?: React.ReactNode
  emptyNotice?: React.ReactNode
}

export default function SubscriptionChipListSection({
  title,
  count,
  searchLabel,
  noMatchesLabel,
  items,
  collapsible = false,
  initialExpanded = false,
  contentId,
  headerExtra,
  emptyNotice,
}: SubscriptionChipListSectionProps): React.JSX.Element {
  const [expanded, setExpanded] = useState(collapsible ? initialExpanded : true)
  const [search, setSearch] = useState('')

  const matches = items.filter((item) =>
    item.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()),
  )

  const headerTitle = collapsible ? (
    <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
      <Button
        color="inherit"
        onClick={() => setExpanded(!expanded)}
        aria-expanded={expanded}
        aria-controls={contentId}
        aria-label={title}
        sx={{
          p: 0,
          minWidth: 'auto',
          textTransform: 'none',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 1,
          color: 'text.primary',
          '&:hover': { bgcolor: 'transparent', opacity: 0.8 },
        }}
      >
        <Typography variant="h6" fontWeight={700} component="span">
          {title}
        </Typography>
        <Chip
          size="small"
          label={count.toString()}
          color="primary"
          variant="filled"
          sx={{ height: 20, fontSize: '0.75rem', fontWeight: 600 }}
        />
        <ChevronDown
          size={20}
          style={{
            transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
            transition: 'transform 200ms ease-in-out',
          }}
        />
      </Button>
      {headerExtra}
    </Stack>
  ) : (
    <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
      <Typography variant="h6" fontWeight={700} component="span">
        {title}
      </Typography>
      <Chip
        size="small"
        label={count.toString()}
        color="primary"
        variant="filled"
        sx={{ height: 20, fontSize: '0.75rem', fontWeight: 600 }}
      />
      {headerExtra}
    </Stack>
  )

  const cardContent = (
    <CardContent id={contentId} sx={{ p: 3 }}>
      {items.length === 0 ? (
        emptyNotice || null
      ) : (
        <Stack spacing={2}>
          <Stack direction="row" useFlexGap flexWrap="wrap" gap={1}>
            {matches.map((item) => (
              <Chip
                key={item}
                label={item}
                title={item}
                variant="outlined"
                sx={{ maxWidth: '100%' }}
              />
            ))}
          </Stack>
          {!matches.length ? <Typography role="status">{noMatchesLabel}</Typography> : null}
        </Stack>
      )}
    </CardContent>
  )

  return (
    <Card sx={{ border: 1, borderColor: 'divider', boxShadow: 1 }}>
      <CardHeader
        sx={{
          alignItems: { xs: 'flex-start', sm: 'center' },
          flexWrap: { xs: 'wrap', sm: 'nowrap' },
          gap: { xs: 2, sm: 0 },
          '& .MuiCardHeader-action': {
            alignSelf: { xs: 'stretch', sm: 'center' },
            m: 0,
            width: { xs: '100%', sm: 'auto' },
          },
        }}
        title={headerTitle}
        action={
          items.length > 0 ? (
            <TextField
              size="small"
              label={searchLabel}
              value={search}
              sx={{ width: { xs: '100%', sm: 280 }, maxWidth: '100%' }}
              onChange={(event) => {
                setSearch(event.target.value)
                if (collapsible) {
                  setExpanded(true)
                }
              }}
            />
          ) : null
        }
      />
      {collapsible ? (
        <Collapse in={expanded}>
          <Divider />
          {cardContent}
        </Collapse>
      ) : (
        <>
          <Divider />
          {cardContent}
        </>
      )}
    </Card>
  )
}

SubscriptionChipListSection.defaultProps = {
  collapsible: false,
  initialExpanded: false,
  contentId: undefined,
  headerExtra: undefined,
  emptyNotice: undefined,
}

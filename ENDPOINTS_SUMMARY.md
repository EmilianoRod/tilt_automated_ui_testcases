# Endpoints Summary (guessed from Rails controllers)

## V1 – api
- Base: `/api/v1/api`
- `GET /api/v1/api` – index
- `GET /api/v1/api/:id` – show
- `POST /api/v1/api` – create
- `PUT /api/v1/api/:id` – update
- `DELETE /api/v1/api/:id` – destroy

## V1 – user_assessments
- Base: `/api/v1/user_assessments`
- `GET /api/v1/user_assessments` – index
- `GET /api/v1/user_assessments/:id` – show
- `POST /api/v1/user_assessments` – create
- `PUT /api/v1/user_assessments/:id` – update
- `DELETE /api/v1/user_assessments/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v1/user_assessments/pdf`
  - `GET /api/v1/user_assessments/results`
  - `GET /api/v1/user_assessments/filtered_user_assessments`
  - `GET /api/v1/user_assessments/user_assessment`
  - `GET /api/v1/user_assessments/user`

## V1 – user_assessments/answers
- Base: `/api/v1/user_assessments/answers`
- `GET /api/v1/user_assessments/answers` – index
- `GET /api/v1/user_assessments/answers/:id` – show
- `POST /api/v1/user_assessments/answers` – create
- `PUT /api/v1/user_assessments/answers/:id` – update
- `DELETE /api/v1/user_assessments/answers/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v1/user_assessments/answers/user_assessment`

## V1 – users
- Base: `/api/v1/users`
- `GET /api/v1/users` – index
- `GET /api/v1/users/:id` – show
- `POST /api/v1/users` – create
- `PUT /api/v1/users/:id` – update
- `DELETE /api/v1/users/:id` – destroy

## V2 – api
- Base: `/api/v2/api`
- `GET /api/v2/api` – index
- `GET /api/v2/api/:id` – show
- `POST /api/v2/api` – create
- `PUT /api/v2/api/:id` – update
- `DELETE /api/v2/api/:id` – destroy

## V2 – assessments
- Base: `/api/v2/assessments`
- `GET /api/v2/assessments` – index
- `GET /api/v2/assessments/:id` – show
- `POST /api/v2/assessments` – create
- `PUT /api/v2/assessments/:id` – update
- `DELETE /api/v2/assessments/:id` – destroy

## V2 – coupon_codes
- Base: `/api/v2/coupon_codes`
- `GET /api/v2/coupon_codes` – index
- `GET /api/v2/coupon_codes/:id` – show
- `POST /api/v2/coupon_codes` – create
- `PUT /api/v2/coupon_codes/:id` – update
- `DELETE /api/v2/coupon_codes/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/coupon_codes/validate`

## V2 – google_auths
- Base: `/api/v2/google_auths`
- `GET /api/v2/google_auths` – index
- `GET /api/v2/google_auths/:id` – show
- `POST /api/v2/google_auths` – create
- `PUT /api/v2/google_auths/:id` – update
- `DELETE /api/v2/google_auths/:id` – destroy

## V2 – impersonations
- Base: `/api/v2/impersonations`
- `GET /api/v2/impersonations` – index
- `GET /api/v2/impersonations/:id` – show
- `POST /api/v2/impersonations` – create
- `PUT /api/v2/impersonations/:id` – update
- `DELETE /api/v2/impersonations/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/impersonations/stop`
  - `GET /api/v2/impersonations/decode_token`

## V2 – individuals
- Base: `/api/v2/individuals`
- `GET /api/v2/individuals` – index
- `GET /api/v2/individuals/:id` – show
- `POST /api/v2/individuals` – create
- `PUT /api/v2/individuals/:id` – update
- `DELETE /api/v2/individuals/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/individuals/apply_filters`
  - `PUT /api/v2/individuals/update_order_subscriptions`

## V2 – maintenance_windows
- Base: `/api/v2/maintenance_windows`
- `GET /api/v2/maintenance_windows` – index
- `GET /api/v2/maintenance_windows/:id` – show
- `POST /api/v2/maintenance_windows` – create
- `PUT /api/v2/maintenance_windows/:id` – update
- `DELETE /api/v2/maintenance_windows/:id` – destroy

## V2 – organizations
- Base: `/api/v2/organizations`
- `GET /api/v2/organizations` – index
- `GET /api/v2/organizations/:id` – show
- `POST /api/v2/organizations` – create
- `PUT /api/v2/organizations/:id` – update
- `DELETE /api/v2/organizations/:id` – destroy

## V2 – passwords
- Base: `/api/v2/passwords`
- `GET /api/v2/passwords` – index
- `GET /api/v2/passwords/:id` – show
- `POST /api/v2/passwords` – create
- `PUT /api/v2/passwords/:id` – update
- `DELETE /api/v2/passwords/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/passwords/forgot`
  - `GET /api/v2/passwords/reset`

## V2 – pdfs
- Base: `/api/v2/pdfs`
- `GET /api/v2/pdfs` – index
- `GET /api/v2/pdfs/:id` – show
- `POST /api/v2/pdfs` – create
- `PUT /api/v2/pdfs/:id` – update
- `DELETE /api/v2/pdfs/:id` – destroy

## V2 – shop/checkouts
- Base: `/api/v2/shop/checkouts`
- `GET /api/v2/shop/checkouts` – index
- `GET /api/v2/shop/checkouts/:id` – show
- `POST /api/v2/shop/checkouts` – create
- `PUT /api/v2/shop/checkouts/:id` – update
- `DELETE /api/v2/shop/checkouts/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/shop/checkouts/build_discounts`
  - `POST /api/v2/shop/checkouts/create_order`
  - `POST /api/v2/shop/checkouts/create_stripe_session`
  - `GET /api/v2/shop/checkouts/stripe_line_items`
  - `GET /api/v2/shop/checkouts/stripe_metadata_subscriptions`
  - `GET /api/v2/shop/checkouts/validate_coupon_code`

## V2 – shop/orders
- Base: `/api/v2/shop/orders`
- `GET /api/v2/shop/orders` – index
- `GET /api/v2/shop/orders/:id` – show
- `POST /api/v2/shop/orders` – create
- `PUT /api/v2/shop/orders/:id` – update
- `DELETE /api/v2/shop/orders/:id` – destroy
- Custom actions (guessed verbs):
  - `POST /api/v2/shop/orders/create_order`
  - `GET /api/v2/shop/orders/metadata_subscriptions`

## V2 – shop/previews
- Base: `/api/v2/shop/previews`
- `GET /api/v2/shop/previews` – index
- `GET /api/v2/shop/previews/:id` – show
- `POST /api/v2/shop/previews` – create
- `PUT /api/v2/shop/previews/:id` – update
- `DELETE /api/v2/shop/previews/:id` – destroy

## V2 – teams
- Base: `/api/v2/teams`
- `GET /api/v2/teams` – index
- `GET /api/v2/teams/:id` – show
- `POST /api/v2/teams` – create
- `PUT /api/v2/teams/:id` – update
- `DELETE /api/v2/teams/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/teams/apply_filters`

## V2 – teams/subscriptions_needed
- Base: `/api/v2/teams/subscriptions_needed`
- `GET /api/v2/teams/subscriptions_needed` – index
- `GET /api/v2/teams/subscriptions_needed/:id` – show
- `POST /api/v2/teams/subscriptions_needed` – create
- `PUT /api/v2/teams/subscriptions_needed/:id` – update
- `DELETE /api/v2/teams/subscriptions_needed/:id` – destroy

## V2 – teams/user_assessments
- Base: `/api/v2/teams/user_assessments`
- `GET /api/v2/teams/user_assessments` – index
- `GET /api/v2/teams/user_assessments/:id` – show
- `POST /api/v2/teams/user_assessments` – create
- `PUT /api/v2/teams/user_assessments/:id` – update
- `DELETE /api/v2/teams/user_assessments/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/teams/user_assessments/team`
  - `GET /api/v2/teams/user_assessments/team_users`
  - `GET /api/v2/teams/user_assessments/filtered_user_assessments`

## V2 – teams/users
- Base: `/api/v2/teams/users`
- `GET /api/v2/teams/users` – index
- `GET /api/v2/teams/users/:id` – show
- `POST /api/v2/teams/users` – create
- `PUT /api/v2/teams/users/:id` – update
- `DELETE /api/v2/teams/users/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/teams/users/team`

## V2 – user_assessments
- Base: `/api/v2/user_assessments`
- `GET /api/v2/user_assessments` – index
- `GET /api/v2/user_assessments/:id` – show
- `POST /api/v2/user_assessments` – create
- `PUT /api/v2/user_assessments/:id` – update
- `DELETE /api/v2/user_assessments/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/user_assessments/pdf`
  - `GET /api/v2/user_assessments/results`
  - `GET /api/v2/user_assessments/reminder`
  - `GET /api/v2/user_assessments/filtered_user_assessments`
  - `GET /api/v2/user_assessments/user_assessment`

## V2 – user_assessments/answers
- Base: `/api/v2/user_assessments/answers`
- `GET /api/v2/user_assessments/answers` – index
- `GET /api/v2/user_assessments/answers/:id` – show
- `POST /api/v2/user_assessments/answers` – create
- `PUT /api/v2/user_assessments/answers/:id` – update
- `DELETE /api/v2/user_assessments/answers/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/user_assessments/answers/user_assessment`

## V2 – users
- Base: `/api/v2/users`
- `GET /api/v2/users` – index
- `GET /api/v2/users/:id` – show
- `POST /api/v2/users` – create
- `PUT /api/v2/users/:id` – update
- `DELETE /api/v2/users/:id` – destroy
- Custom actions (guessed verbs):
  - `PUT /api/v2/users/update_password`
  - `GET /api/v2/users/redhat_tilts`
  - `POST /api/v2/users/send_confirmation_email`
  - `GET /api/v2/users/apply_filters`
  - `GET /api/v2/users/user`
  - `GET /api/v2/users/redhat_users_with_assessments`

## V2 – users/me
- Base: `/api/v2/users/me`
- `GET /api/v2/users/me` – index
- `GET /api/v2/users/me/:id` – show
- `POST /api/v2/users/me` – create
- `PUT /api/v2/users/me/:id` – update
- `DELETE /api/v2/users/me/:id` – destroy

## V2 – users/sessions
- Base: `/api/v2/users/sessions`
- `GET /api/v2/users/sessions` – index
- `GET /api/v2/users/sessions/:id` – show
- `POST /api/v2/users/sessions` – create
- `PUT /api/v2/users/sessions/:id` – update
- `DELETE /api/v2/users/sessions/:id` – destroy
- Custom actions (guessed verbs):
  - `GET /api/v2/users/sessions/handle_organization_token_after_login`
  - `GET /api/v2/users/sessions/extract_token_and_org_type`
  - `GET /api/v2/users/sessions/process_redhat_token`

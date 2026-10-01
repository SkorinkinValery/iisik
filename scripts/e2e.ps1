param([string]$Endpoint = 'http://localhost:8080/graphql')

$ErrorActionPreference = 'Stop'

function Invoke-GraphQl {
    param(
        [Parameter(Mandatory)][string]$Query,
        [hashtable]$Variables = @{},
        [string]$Token,
        [switch]$ExpectError
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $body = @{ query = $Query; variables = $Variables } | ConvertTo-Json -Depth 20 -Compress
    try {
        $response = Invoke-RestMethod -Uri $Endpoint -Method Post -ContentType 'application/json' -Headers $headers -Body $body
    } catch {
        if ($ExpectError) { return $_ }
        throw
    }
    if ($ExpectError) {
        if (-not $response.errors) { throw 'GraphQL operation unexpectedly succeeded' }
        return $response
    }
    if ($response.errors) { throw ($response.errors | ConvertTo-Json -Depth 10) }
    return $response.data
}

function Login([string]$Email, [string]$Password) {
    $data = Invoke-GraphQl -Query 'mutation Login($input: LoginInput!) { login(input: $input) { accessToken user { id email role } } }' -Variables @{ input = @{ email = $Email; password = $Password } }
    return $data.login
}

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$client = Login 'client@example.com' 'Client123!'
$employee = Login 'employee@example.com' 'Employee123!'
$admin = Login 'admin@example.com' 'Admin123!'

$category = (Invoke-GraphQl -Token $admin.accessToken -Query 'mutation Category($input: UpsertDeviceCategoryInput!) { upsertDeviceCategory(input: $input) { id name active } }' -Variables @{ input = @{ name = "Тестовая категория $suffix"; active = $true } }).upsertDeviceCategory
$service = (Invoke-GraphQl -Token $admin.accessToken -Query 'mutation Service($input: UpsertServiceInput!) { upsertService(input: $input) { id name price active } }' -Variables @{ input = @{ name = "Тестовая услуга $suffix"; price = 37.50; active = $true } }).upsertService
$part = (Invoke-GraphQl -Token $admin.accessToken -Query 'mutation Part($input: UpsertSparePartInput!) { upsertSparePart(input: $input) { id sku name price stockQuantity active } }' -Variables @{ input = @{ sku = "E2E-$suffix"; name = "Тестовая деталь $suffix"; price = 112.25; stockQuantity = 4; active = $true } }).upsertSparePart

$device = (Invoke-GraphQl -Token $client.accessToken -Query 'mutation Device($input: UpsertDeviceInput!) { upsertDevice(input: $input) { id clientId categoryId serialNumber } }' -Variables @{ input = @{ categoryId = $category.id; brand = 'Bosch'; model = 'E2E'; serialNumber = "SER-$suffix" } }).upsertDevice
$request = (Invoke-GraphQl -Token $client.accessToken -Query 'mutation Request($input: CreateRepairRequestInput!) { createRepairRequest(input: $input) { id clientId deviceId status priority } }' -Variables @{ input = @{ deviceId = $device.id; problemDescription = 'Устройство не запускается после включения'; priority = 4 } }).createRepairRequest

$outsiderEmail = "outsider-$suffix@example.com"
$outsiderUser = (Invoke-GraphQl -Query 'mutation Register($input: RegisterInput!) { register(input: $input) { id email role enabled } }' -Variables @{ input = @{ email = $outsiderEmail; password = 'Outsider123!'; fullName = 'Чужой клиент'; phone = '+375290000001' } }).register
Invoke-GraphQl -Token $admin.accessToken -Query 'mutation Role($id: ID!) { changeUserRole(userId: $id, role: EMPLOYEE) { id role } }' -Variables @{ id = $outsiderUser.id } | Out-Null
Invoke-GraphQl -Token $admin.accessToken -Query 'mutation Role($id: ID!) { changeUserRole(userId: $id, role: CLIENT) { id role } }' -Variables @{ id = $outsiderUser.id } | Out-Null
Invoke-GraphQl -Token $admin.accessToken -Query 'mutation Enabled($id: ID!, $enabled: Boolean!) { setUserEnabled(userId: $id, enabled: $enabled) { id enabled } }' -Variables @{ id = $outsiderUser.id; enabled = $false } | Out-Null
Invoke-GraphQl -Token $admin.accessToken -Query 'mutation Enabled($id: ID!, $enabled: Boolean!) { setUserEnabled(userId: $id, enabled: $enabled) { id enabled } }' -Variables @{ id = $outsiderUser.id; enabled = $true } | Out-Null
$outsider = Login $outsiderEmail 'Outsider123!'
Invoke-GraphQl -Token $outsider.accessToken -ExpectError -Query 'query Request($id: ID!) { repairRequest(id: $id) { id } }' -Variables @{ id = $request.id } | Out-Null
Invoke-GraphQl -Token $client.accessToken -ExpectError -Query 'query Analytics($from: String!, $to: String!) { repairAnalytics(period: {from: $from, to: $to}) { repairsCount } }' -Variables @{ from = '2020-01-01T00:00:00Z'; to = '2030-12-31T23:59:59Z' } | Out-Null

Invoke-GraphQl -Token $employee.accessToken -Query 'mutation Status($input: ChangeRepairRequestStatusInput!) { changeRepairRequestStatus(input: $input) { id status } }' -Variables @{ input = @{ requestId = $request.id; status = 'DIAGNOSING' } } | Out-Null
$order = (Invoke-GraphQl -Token $employee.accessToken -Query 'mutation Diagnose($input: DiagnoseWorkOrderInput!) { diagnoseAndCreateWorkOrder(input: $input) { id repairRequestId status totalAmount } }' -Variables @{ input = @{ requestId = $request.id; diagnosis = 'Неисправность насоса подтверждена диагностикой' } }).diagnoseAndCreateWorkOrder
$order = (Invoke-GraphQl -Token $employee.accessToken -Query 'mutation AddService($input: AddServiceInput!) { addServiceToWorkOrder(input: $input) { id totalAmount services { serviceId quantity lineTotal } } }' -Variables @{ input = @{ workOrderId = $order.id; serviceId = $service.id; quantity = 2 } }).addServiceToWorkOrder
$order = (Invoke-GraphQl -Token $employee.accessToken -Query 'mutation AddPart($input: AddPartInput!) { addPartToWorkOrder(input: $input) { id totalAmount parts { partId quantity lineTotal } } }' -Variables @{ input = @{ workOrderId = $order.id; partId = $part.id; quantity = 1; idempotencyKey = "part-$suffix" } }).addPartToWorkOrder
if ([decimal]$order.totalAmount -ne 187.25) { throw "Unexpected work order total: $($order.totalAmount)" }
$estimated = (Invoke-GraphQl -Token $employee.accessToken -Query 'mutation Estimate($input: EstimateRepairCostInput!) { estimateRepairCost(input: $input) { id totalAmount } }' -Variables @{ input = @{ requestId = $request.id } }).estimateRepairCost
if ([decimal]$estimated.totalAmount -ne 187.25) { throw 'Compatibility estimateRepairCost returned an incorrect total' }

foreach ($state in @('IN_PROGRESS', 'READY')) {
    Invoke-GraphQl -Token $employee.accessToken -Query 'mutation Status($input: ChangeWorkOrderStatusInput!) { changeWorkOrderStatus(input: $input) { id status } }' -Variables @{ input = @{ workOrderId = $order.id; status = $state } } | Out-Null
    Invoke-GraphQl -Token $employee.accessToken -Query 'mutation Status($input: ChangeRepairRequestStatusInput!) { changeRepairRequestStatus(input: $input) { id status } }' -Variables @{ input = @{ requestId = $request.id; status = $state } } | Out-Null
}

$paymentKey = "payment-$suffix"
$payment1 = (Invoke-GraphQl -Token $client.accessToken -Query 'mutation Pay($input: RegisterPaymentInput!) { registerPayment(input: $input) { id status amount clientId } }' -Variables @{ input = @{ workOrderId = $order.id; method = 'CARD'; idempotencyKey = $paymentKey } }).registerPayment
$payment2 = (Invoke-GraphQl -Token $client.accessToken -Query 'mutation Pay($input: RegisterPaymentInput!) { registerPayment(input: $input) { id status amount clientId } }' -Variables @{ input = @{ workOrderId = $order.id; method = 'CARD'; idempotencyKey = $paymentKey } }).registerPayment
if ($payment1.id -ne $payment2.id) { throw 'Payment idempotency check failed' }

Invoke-GraphQl -Token $employee.accessToken -Query 'mutation Status($input: ChangeWorkOrderStatusInput!) { changeWorkOrderStatus(input: $input) { id status } }' -Variables @{ input = @{ workOrderId = $order.id; status = 'COMPLETED' } } | Out-Null
Invoke-GraphQl -Token $employee.accessToken -Query 'mutation Status($input: ChangeRepairRequestStatusInput!) { changeRepairRequestStatus(input: $input) { id status } }' -Variables @{ input = @{ requestId = $request.id; status = 'COMPLETED' } } | Out-Null

Start-Sleep -Seconds 4
$requests = (Invoke-GraphQl -Token $client.accessToken -Query 'query Requests($category: ID!) { repairRequests(filter: {status: COMPLETED, categoryId: $category}, sort: {field: COST, direction: DESC}, page: 0, size: 10) { items { id status estimatedCost device { id } workOrder { id totalAmount } } totalElements } }' -Variables @{ category = $category.id }).repairRequests
if (-not ($requests.items.id -contains $request.id)) { throw 'Filtered request query did not return the created request' }
$payments = (Invoke-GraphQl -Token $client.accessToken -Query 'query Payments($id: ID!) { payments(workOrderId: $id) { id status amount } }' -Variables @{ id = $order.id }).payments
if ($payments.Count -ne 1) { throw 'Idempotent payment was persisted more than once' }
$notifications = (Invoke-GraphQl -Token $client.accessToken -Query 'query { notifications { id type message read } }').notifications
if ($notifications.Count -lt 1) { throw 'No client notifications were created from Kafka events' }
Invoke-GraphQl -Token $client.accessToken -Query 'mutation Read($id: ID!) { markNotificationAsRead(id: $id) { id read } }' -Variables @{ id = $notifications[0].id } | Out-Null
$analytics = (Invoke-GraphQl -Token $admin.accessToken -Query 'query Analytics($from: String!, $to: String!) { repairAnalytics(period: {from: $from, to: $to}) { repairsCount completedCount revenue mostRequestedService popularServices { name usageCount revenue } employeeWorkload { employeeId assignedCount completedCount } } users { id email role enabled } }' -Variables @{ from = '2020-01-01T00:00:00Z'; to = '2030-12-31T23:59:59Z' })
if ([int]$analytics.repairAnalytics.repairsCount -lt 1 -or [decimal]$analytics.repairAnalytics.revenue -lt 187.25) { throw 'Analytics was not updated from Kafka events' }

Invoke-GraphQl -Token $outsider.accessToken -Query 'mutation { logout }' | Out-Null
Invoke-GraphQl -Token $outsider.accessToken -ExpectError -Query 'query { me { id } }' | Out-Null

[pscustomobject]@{
    result = 'PASS'
    deviceId = $device.id
    repairRequestId = $request.id
    workOrderId = $order.id
    paymentId = $payment1.id
    totalAmount = $payment1.amount
    notifications = $notifications.Count
    analyticsRepairs = $analytics.repairAnalytics.repairsCount
    analyticsRevenue = $analytics.repairAnalytics.revenue
} | ConvertTo-Json -Depth 5

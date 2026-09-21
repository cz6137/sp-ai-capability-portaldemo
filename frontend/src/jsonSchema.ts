type JsonObject = Record<string, unknown>

export function validateJsonSchema(value: unknown, schema: unknown): string[] {
  if (!isObject(schema)) return ['Schema 根节点必须是对象']
  return validateNode(value, schema, schema, '$')
}

function validateNode(value: unknown, schema: JsonObject, root: JsonObject, path: string): string[] {
  if (typeof schema.$ref === 'string') {
    const target = resolveRef(root, schema.$ref)
    return target ? validateNode(value, target, root, path) : [`${path}: Schema 引用不存在 ${schema.$ref}`]
  }

  const errors: string[] = []
  const expectedType = schema.type
  if (typeof expectedType === 'string' && !matchesType(value, expectedType)) {
    return [`${path}: 应为${typeLabel(expectedType)}`]
  }
  if (Array.isArray(schema.enum) && !schema.enum.some(item => equal(item, value))) errors.push(`${path}: 值不在允许范围内`)
  if ('const' in schema && !equal(schema.const, value)) errors.push(`${path}: 值不符合固定要求`)

  if (typeof value === 'string') {
    if (typeof schema.minLength === 'number' && value.length < schema.minLength) errors.push(`${path}: 文字长度不足`)
    if (typeof schema.pattern === 'string' && !new RegExp(schema.pattern).test(value)) errors.push(`${path}: 格式不符`)
    if (schema.format === 'date' && !/^\d{4}-\d{2}-\d{2}$/.test(value)) errors.push(`${path}: 日期应为 YYYY-MM-DD`)
  }
  if (typeof value === 'number') {
    if (typeof schema.minimum === 'number' && value < schema.minimum) errors.push(`${path}: 小于最小值 ${schema.minimum}`)
    if (typeof schema.maximum === 'number' && value > schema.maximum) errors.push(`${path}: 大于最大值 ${schema.maximum}`)
  }
  if (Array.isArray(value)) {
    if (typeof schema.minItems === 'number' && value.length < schema.minItems) errors.push(`${path}: 至少需要 ${schema.minItems} 项`)
    if (schema.uniqueItems === true && new Set(value.map(item => JSON.stringify(item))).size !== value.length) errors.push(`${path}: 列表包含重复项`)
    if (isObject(schema.items)) value.forEach((item, index) => errors.push(...validateNode(item, schema.items as JsonObject, root, `${path}/${index}`)))
  }
  if (isObject(value)) {
    const properties = isObject(schema.properties) ? schema.properties : {}
    if (Array.isArray(schema.required)) {
      for (const key of schema.required) if (typeof key === 'string' && !(key in value)) errors.push(`${path}/${key}: 缺少必填字段`)
    }
    for (const [key, item] of Object.entries(value)) {
      if (isObject(properties[key])) errors.push(...validateNode(item, properties[key] as JsonObject, root, `${path}/${escapePointer(key)}`))
      else if (schema.additionalProperties === false) errors.push(`${path}/${escapePointer(key)}: 未知字段`)
      else if (isObject(schema.additionalProperties)) errors.push(...validateNode(item, schema.additionalProperties, root, `${path}/${escapePointer(key)}`))
    }
  }
  if (Array.isArray(schema.allOf)) {
    for (const branch of schema.allOf) if (isObject(branch)) errors.push(...validateNode(value, branch, root, path))
  }
  if (isObject(schema.if) && validateNode(value, schema.if, root, path).length === 0 && isObject(schema.then)) {
    errors.push(...validateNode(value, schema.then, root, path))
  }
  return errors
}

function resolveRef(root: JsonObject, ref: string): JsonObject | undefined {
  if (!ref.startsWith('#/')) return undefined
  let current: unknown = root
  for (const token of ref.slice(2).split('/').map(part => part.replace(/~1/g, '/').replace(/~0/g, '~'))) {
    if (!isObject(current)) return undefined
    current = current[token]
  }
  return isObject(current) ? current : undefined
}

function matchesType(value: unknown, type: string) {
  if (type === 'object') return isObject(value)
  if (type === 'array') return Array.isArray(value)
  if (type === 'integer') return Number.isInteger(value)
  if (type === 'number') return typeof value === 'number' && Number.isFinite(value)
  if (type === 'null') return value === null
  return typeof value === type
}

function typeLabel(type: string) {
  return ({ object: '对象', array: '列表', string: '文字', integer: '整数', number: '数字', boolean: '布尔值', null: '空值' } as Record<string, string>)[type] || type
}

function isObject(value: unknown): value is JsonObject { return Boolean(value) && typeof value === 'object' && !Array.isArray(value) }
function equal(left: unknown, right: unknown) { return JSON.stringify(left) === JSON.stringify(right) }
function escapePointer(value: string) { return value.replace(/~/g, '~0').replace(/\//g, '~1') }

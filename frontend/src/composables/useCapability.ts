import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { capabilityErrorMessage, type CapabilitySelection } from '../capabilityAccess'
import { capabilityReader } from '../capabilityReader'

export function useCapability(slug: Readonly<Ref<string>>) {
  const selected = ref<CapabilitySelection>()
  const loading = ref(false)
  const error = ref('')
  let request = 0
  async function load() {
    const current = ++request
    selected.value = undefined; error.value = ''; loading.value = true
    try {
      const result = await capabilityReader.get(slug.value)
      if (current === request) selected.value = result
    } catch (cause) {
      if (current === request) error.value = capabilityErrorMessage(cause)
    } finally { if (current === request) loading.value = false }
  }
  watch(slug, load, { immediate: true })
  onBeforeUnmount(() => { request++ })
  return { selected, loading, error, load, manifest: computed(() => selected.value?.manifest) }
}

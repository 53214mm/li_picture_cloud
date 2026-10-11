// Display only an already-observed pending proposal. Never fetch, generate,
// reinterpret its contents or treat character expression as contract authority.
export function selectCompanionProposalPreview(observation, presentation) {
  const proposal = observation?.proposal
  const preview = proposal?.preview
  if (presentation?.version !== 1 || presentation.availability !== 'ready' || presentation.attention !== 'proposal' ||
      proposal?.status !== 'PENDING' || proposal.loading === true || proposal.error === true ||
      !preview || typeof preview.content !== 'string' || !preview.content.trim()) return null
  const id = typeof preview.id === 'number' && Number.isSafeInteger(preview.id) ? String(preview.id) : preview.id
  if (typeof id !== 'string' || !/^[1-9]\d*$/.test(id)) return null
  return Object.freeze({ id, content: preview.content })
}

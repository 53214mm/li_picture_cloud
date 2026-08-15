import request from './request'

export const listRecipeTemplates = () => request.get('/recipe/templates')
export const createRecipe = data => request.post('/recipe', data)
export const createRecipeFromTemplate = data => request.post('/recipe/from-template', data)
export const listRecipes = (limit = 20) => request.get('/recipe', { params: { limit } })
export const getRecipeDetail = id => request.get(`/recipe/${id}`)
export const publishRecipeVersion = (id, data) => request.post(`/recipe/${id}/versions`, data)
export const enableRecipe = id => request.post(`/recipe/${id}/enable`)
export const disableRecipe = id => request.post(`/recipe/${id}/disable`)
export const deleteRecipe = id => request.delete(`/recipe/${id}`)

export const dryRunRecipe = (id, data) => request.post(`/recipe/${id}/dry-run`, data)
export const executeRecipe = (id, executionId, data) =>
  request.post(`/recipe/${id}/executions/${executionId}/execute`, data)
export const listRecipeExecutions = (id, limit = 20) =>
  request.get(`/recipe/${id}/executions`, { params: { limit } })
export const listMyRecipeExecutions = (limit = 20) =>
  request.get('/recipe/executions', { params: { limit } })

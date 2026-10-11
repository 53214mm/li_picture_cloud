// Exercise the actual room navigation; hidden sections stay mounted.
export async function visitRoomZone(page, label) {
  await page.getByRole('navigation', { name: '小屋区域' }).getByRole('link', { name: new RegExp(label) }).click()
}

export async function openDisclosure(page, testId) {
  const details = page.getByTestId(testId)
  if (!await details.evaluate(element => element.open)) await details.locator('summary').click()
}

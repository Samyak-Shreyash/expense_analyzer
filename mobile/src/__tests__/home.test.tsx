import { render, screen } from '@testing-library/react-native'
import Home from '../app/index'

it('shows the placeholder screen', async () => {
  await render(<Home />)
  expect(screen.getByText('Expense Analyzer')).toBeTruthy()
})

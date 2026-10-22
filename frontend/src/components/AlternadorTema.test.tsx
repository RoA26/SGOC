import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { AlternadorTema } from './AlternadorTema'

describe('AlternadorTema', () => {
  beforeEach(() => {
    delete document.documentElement.dataset.theme
    localStorage.clear()
  })

  it('alterna el tema en <html>, lo indica con aria-pressed y lo recuerda', async () => {
    const user = userEvent.setup()
    render(<AlternadorTema />)
    const boton = screen.getByRole('button', { name: 'Modo oscuro' })
    expect(boton).toHaveAttribute('aria-pressed', 'false')

    await user.click(boton)
    expect(boton).toHaveAttribute('aria-pressed', 'true')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('nexora-tema')).toBe('dark')

    await user.click(boton)
    expect(boton).toHaveAttribute('aria-pressed', 'false')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('nexora-tema')).toBe('light')
  })
})

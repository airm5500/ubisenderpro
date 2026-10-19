import { Component, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { VerificationNumeros } from '../../core/modeles';
import { TelephonePipe } from '../../core/format';

const RESULTATS: Record<string, string> = {
  SUR_WHATSAPP: 'Sur WhatsApp', ABSENT: 'Pas sur WhatsApp', FORMAT_INVALIDE: 'Format invalide', NON_VERIFIE: 'Non vérifié'
};
const FONCTIONS: Record<string, string> = {
  PRINCIPAL: 'Principal', TITULAIRE: 'Titulaire', ASSISTANT: 'Assistant', CHARGE_COMMANDE: 'Commandes', COMPTABLE: 'Comptable', SECONDAIRE: 'Autre'
};

/** Verifie des numeros colles a la main, ou ceux des clients selectionnes. */
@Component({
  selector: 'app-verification-numeros',
  imports: [FormsModule, DialogModule, TelephonePipe],
  templateUrl: './verification-numeros.html'
})
export class VerificationNumerosDialogue {
  private readonly api = inject(Api);
  protected readonly RESULTATS = RESULTATS;
  protected readonly FONCTIONS = FONCTIONS;

  readonly ouvert = model(false);
  readonly clientIds = input<number[]>([]);

  saisie = '';
  protected readonly resultat = signal<VerificationNumeros | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  preparer(): void {
    this.saisie = '';
    this.resultat.set(null);
    this.erreur.set(null);
    if (this.clientIds().length) this.verifier();
  }

  verifier(): void {
    const numeros = this.saisie.split(/[\n;,]+/).map(n => n.trim()).filter(Boolean);
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<VerificationNumeros>('/api/clients/verification-numeros', { numeros, clientIds: this.clientIds() }).subscribe({
      next: r => { this.enCours.set(false); this.resultat.set(r); },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Vérification impossible.'); }
    });
  }

  exporter(): void {
    const r = this.resultat();
    if (!r) return;
    const lignes = [['Numéro saisi', 'Numéro international', 'Client', 'Fonction', 'Résultat'],
      ...r.resultats.map(l => [l.saisi, l.numero ?? '', l.client ?? '', FONCTIONS[l.fonction ?? ''] ?? '', RESULTATS[l.resultat] ?? l.resultat])];
    const csv = '﻿' + lignes.map(l => l.map(v => '"' + String(v).replace(/"/g, '""') + '"').join(';')).join('\r\n');
    const lien = document.createElement('a');
    lien.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    lien.download = 'verification-numeros.csv';
    lien.click();
    URL.revokeObjectURL(lien.href);
  }
}

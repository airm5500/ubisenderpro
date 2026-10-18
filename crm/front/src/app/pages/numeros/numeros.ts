import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { EtatSession, SessionAgent } from '../../core/modeles';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';

@Component({
  selector: 'app-numeros',
  imports: [RouterLink, DialogModule, JourPipe, TelephonePipe],
  templateUrl: './numeros.html'
})
export class Numeros implements OnInit, OnDestroy {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;

  protected readonly sessions = signal<SessionAgent[] | null>(null);
  protected readonly liaison = signal<SessionAgent | null>(null);
  protected readonly etat = signal<EtatSession | null>(null);
  private suivi: ReturnType<typeof setInterval> | null = null;
  private rafraichissement: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.charger();
    this.rafraichissement = setInterval(() => this.charger(), 30000);
  }

  ngOnDestroy(): void {
    this.arreterSuivi();
    if (this.rafraichissement) clearInterval(this.rafraichissement);
  }

  /** Demarre la session WhatsApp Web du numero et affiche le QR jusqu'a la liaison du telephone. */
  connecter(s: SessionAgent): void {
    this.liaison.set(s);
    this.etat.set(null);
    this.api.post<EtatSession>(`/api/whatsapp/sessions/${s.agentId}/connexion`).subscribe({
      next: e => { this.etat.set(e); this.suivre(s); },
      error: () => this.liaison.set(null)
    });
  }

  deconnecter(s: SessionAgent): void {
    this.api.post(`/api/whatsapp/sessions/${s.agentId}/deconnexion`).subscribe(() => {
      this.messages.add({ severity: 'info', summary: 'Téléphone délié', detail: `Les relances de ${s.agentNom} attendent une nouvelle liaison.` });
      this.charger();
    });
  }

  fermer(): void {
    this.arreterSuivi();
    this.liaison.set(null);
    this.charger();
  }

  private suivre(s: SessionAgent): void {
    this.arreterSuivi();
    this.suivi = setInterval(() => {
      this.api.get<EtatSession>(`/api/whatsapp/sessions/${s.agentId}`).subscribe(e => {
        this.etat.set(e);
        if (e.statut === 'CONNECTE') {
          this.arreterSuivi();
          this.messages.add({ severity: 'success', summary: 'Numéro connecté', detail: `${s.numero} est prêt à envoyer les relances.` });
          setTimeout(() => this.fermer(), 1500);
        }
      });
    }, 3000);
  }

  private arreterSuivi(): void {
    if (this.suivi) clearInterval(this.suivi);
    this.suivi = null;
  }

  private charger(): void {
    this.api.get<SessionAgent[]>('/api/whatsapp/sessions').subscribe(s => this.sessions.set(s));
  }
}

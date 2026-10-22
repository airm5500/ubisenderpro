import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { CharteService } from '../../core/charte';
import { EtatSession, SessionAgent } from '../../core/modeles';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';

@Component({
  selector: 'app-numeros',
  imports: [TooltipModule, FormsModule, RouterLink, DialogModule, JourPipe, TelephonePipe],
  templateUrl: './numeros.html'
})
export class Numeros implements OnInit, OnDestroy {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  private readonly charte = inject(CharteService);

  /** Numero jamais relie et mode Meta choisi par defaut dans les Parametres : l'API officielle passe en premier. */
  metaParDefaut(s: SessionAgent): boolean {
    return this.charte.charte()?.modeEnvoiDefaut === 'META' && !s.connecteLe && this.auth.peut('WHATSAPP_META');
  }
  protected readonly L = LIBELLES;

  protected readonly sessions = signal<SessionAgent[] | null>(null);
  protected readonly liaison = signal<SessionAgent | null>(null);
  protected readonly etat = signal<EtatSession | null>(null);
  /** API officielle : compte Meta et numero en cours de bascule. */
  protected readonly bascule = signal<SessionAgent | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  phoneNumberId = '';
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
    if (s.fournisseur === 'META') {
      // Pas de QR : le numero est verifie chez Meta puis les envois reprennent
      this.api.post<EtatSession>(`/api/whatsapp/sessions/${s.agentId}/connexion`).subscribe(e => {
        this.messages.add({ severity: 'success', summary: 'Numéro actif', detail: e.raison ?? s.numero ?? '' });
        this.charger();
      });
      return;
    }
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

  ouvrirBascule(s: SessionAgent): void {
    this.phoneNumberId = s.metaPhoneNumberId ?? '';
    this.erreur.set(null);
    this.bascule.set(s);
  }

  passerMeta(): void {
    const s = this.bascule();
    if (!s) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<SessionAgent>(`/api/whatsapp/sessions/${s.agentId}/meta`, { phoneNumberId: this.phoneNumberId.trim() }).subscribe({
      next: r => {
        this.enCours.set(false);
        this.bascule.set(null);
        this.messages.add({ severity: 'success', summary: 'Numéro passé à l\'API officielle', detail: r.raison ?? '' });
        this.charger();
      },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Passage impossible.'); }
    });
  }

  passerWhatsappWeb(s: SessionAgent): void {
    this.api.post<SessionAgent>(`/api/whatsapp/sessions/${s.agentId}/whatsapp-web`).subscribe(() => {
      this.messages.add({ severity: 'info', summary: 'Retour à WhatsApp Web', detail: `Scannez de nouveau le QR pour ${s.agentNom}.` });
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

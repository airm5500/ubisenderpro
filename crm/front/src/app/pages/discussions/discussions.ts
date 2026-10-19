import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Client, Discussion, MessageDiscussion, Page } from '../../core/modeles';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';

/** Echanges WhatsApp avec les clients : relances, promotions et envois partis, messages recus, reponses ecrites ici. */
@Component({
  selector: 'app-discussions',
  imports: [FormsModule, RouterLink, JourPipe, TelephonePipe],
  templateUrl: './discussions.html',
  styleUrl: './discussions.css'
})
export class Discussions implements OnInit {
  private readonly api = inject(Api);
  protected readonly L = LIBELLES;
  private readonly fil = viewChild<ElementRef<HTMLDivElement>>('fil');

  q = '';
  texte = '';
  protected readonly page = signal<Page<Discussion> | null>(null);
  protected readonly choisie = signal<Discussion | null>(null);
  protected readonly messages = signal<MessageDiscussion[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly titre = computed(() => {
    const d = this.choisie();
    return d ? d.clientNom ?? d.nomAffiche ?? d.numeroClient : '';
  });

  protected readonly redaction = signal(false);
  protected readonly numeros = signal<string[]>([]);
  protected readonly clientsTrouves = signal<Client[]>([]);
  protected readonly erreurRedac = signal<string | null>(null);
  qClient = '';
  redac = { numeroAgent: '', numeroClient: '', contenu: '' };
  private minuteur: ReturnType<typeof setTimeout> | undefined;

  ngOnInit(): void {
    this.rechercher();
  }

  /** Ecrire a un client depuis son numero (ou celui de l'agent remplace). */
  nouvelle(): void {
    this.redac = { numeroAgent: '', numeroClient: '', contenu: '' };
    this.qClient = '';
    this.clientsTrouves.set([]);
    this.erreurRedac.set(null);
    this.redaction.set(true);
    this.api.get<string[]>('/api/discussions/numeros').subscribe(n => {
      this.numeros.set(n);
      this.redac.numeroAgent = n[0] ?? '';
    });
  }

  chercherClients(): void {
    clearTimeout(this.minuteur);
    const q = this.qClient.trim();
    if (q.length < 2) { this.clientsTrouves.set([]); return; }
    this.minuteur = setTimeout(() => this.api.get<Page<Client>>('/api/clients', { q, page: 0, taille: 6 })
      .subscribe(p => this.clientsTrouves.set(p.elements)), 250);
  }

  numerosClient(c: Client): string[] {
    return [...new Set([c.numeroPrincipal, c.numeroSecondaire, ...Object.values(c.numeros ?? {})].filter((n): n is string => !!n))];
  }

  choisirClient(c: Client, numero: string): void {
    this.redac.numeroClient = numero;
    this.qClient = c.raisonSociale;
    this.clientsTrouves.set([]);
  }

  demarrer(): void {
    this.enCours.set(true);
    this.erreurRedac.set(null);
    this.api.postSilencieux<MessageDiscussion>('/api/discussions', this.redac).subscribe({
      next: () => {
        this.enCours.set(false);
        this.redaction.set(false);
        this.choisie.set(null);
        this.rechercher();
      },
      error: e => { this.enCours.set(false); this.erreurRedac.set(e?.error?.message ?? "Le message n'est pas parti."); }
    });
  }

  rechercher(): void {
    this.api.get<Page<Discussion>>('/api/discussions', { q: this.q || null, page: 0, taille: 100 }).subscribe(p => {
      this.page.set(p);
      const c = this.choisie();
      if (!c && p.elements.length) this.ouvrir(p.elements[0]);
    });
  }

  ouvrir(d: Discussion): void {
    this.choisie.set(d);
    this.texte = '';
    this.erreur.set(null);
    this.chargerFil();
  }

  envoyer(): void {
    const d = this.choisie();
    if (!d || !this.texte.trim()) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<MessageDiscussion>('/api/discussions/messages', { numeroAgent: d.numeroAgent, numeroClient: d.numeroClient, contenu: this.texte })
      .subscribe({
        next: () => { this.enCours.set(false); this.texte = ''; this.chargerFil(); },
        error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? "Le message n'est pas parti."); this.chargerFil(); }
      });
  }

  private chargerFil(): void {
    const d = this.choisie();
    if (!d) return;
    this.api.get<MessageDiscussion[]>('/api/discussions/messages', { numeroAgent: d.numeroAgent, numeroClient: d.numeroClient }).subscribe(m => {
      this.messages.set(m);
      setTimeout(() => { const f = this.fil()?.nativeElement; if (f) f.scrollTop = f.scrollHeight; });
    });
  }
}

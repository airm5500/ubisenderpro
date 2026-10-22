import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { CompteMeta } from '../../core/modeles';
import { JourPipe } from '../../core/format';

/** Compte de l'API WhatsApp officielle (Meta) : jeton et secret saisis une fois, chiffres en base, jamais reaffiches. */
@Component({
  selector: 'app-compte-meta',
  imports: [FormsModule, JourPipe],
  templateUrl: './compte-meta.html'
})
export class CompteMetaConfig implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  protected readonly compte = signal<CompteMeta | null>(null);
  protected readonly enCours = signal(false);
  protected readonly parametresMeta = '{{1}}, {{2}}';
  edition = { jetonAcces: '', secretApplication: '', versionApi: 'v21.0' };

  ngOnInit(): void {
    this.api.get<CompteMeta>('/api/whatsapp/meta').subscribe(c => { this.compte.set(c); this.edition.versionApi = c.versionApi; });
  }

  enregistrerCompte(): void {
    this.enCours.set(true);
    this.api.put<CompteMeta>('/api/whatsapp/meta', this.edition).subscribe({
      next: c => {
        this.enCours.set(false);
        this.compte.set(c);
        this.edition = { jetonAcces: '', secretApplication: '', versionApi: c.versionApi };
        this.messages.add({ severity: 'success', summary: 'Compte Meta enregistré' });
      },
      error: () => this.enCours.set(false)
    });
  }

  renouvelerJeton(): void {
    this.api.post<CompteMeta>('/api/whatsapp/meta/jeton-verification').subscribe(c => {
      this.compte.set(c);
      this.messages.add({ severity: 'info', summary: 'Jeton de vérification renouvelé', detail: 'Recopiez-le dans la configuration du webhook chez Meta.' });
    });
  }
}

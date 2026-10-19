import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Promotion } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { PromotionFormulaire } from './promotion-formulaire';
import { STATUTS_PROMOTION } from './statut-promotion';

/** Promotions du Marketing : en preparation, actives, terminees d'office a leur date de fin, archivees. */
@Component({
  selector: 'app-promotions',
  imports: [FormsModule, RouterLink, JourPipe, PromotionFormulaire],
  templateUrl: './promotions.html'
})
export class Promotions implements OnInit {
  private readonly api = inject(Api);
  protected readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly S = STATUTS_PROMOTION;

  protected readonly promotions = signal<Promotion[] | null>(null);
  protected readonly saisie = signal(false);
  criteres = { q: '', statut: 'EN_COURS' };

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.api.get<Promotion[]>('/api/marketing/promotions', this.criteres).subscribe(p => this.promotions.set(p));
  }

  creee(p: Promotion): void {
    this.router.navigate(['/marketing/promotions', p.id]);
  }
}

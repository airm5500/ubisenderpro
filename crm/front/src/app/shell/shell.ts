import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map } from 'rxjs';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../core/auth';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.css'
})
export class Shell {
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);

  /** Module affiche : Marketing sur ses ecrans, Recouvrement ailleurs. */
  protected readonly surMarketing = toSignal(this.router.events.pipe(
    filter(e => e instanceof NavigationEnd),
    map(() => this.router.url.startsWith('/marketing'))
  ), { initialValue: this.router.url.startsWith('/marketing') });
}

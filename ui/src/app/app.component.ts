import { Component, OnInit } from '@angular/core'
import { TranslateService } from '@ngx-translate/core'
import {HomeService} from "./api/home.service";
import {SimulationModel} from "./model/pea.model";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {

  arthas_url="http://10.24.18.12:8081/"

  isCollapsed = false
  lang = 'en'
  KEY_LANG = 'PEA_LANG'

  ngOnInit() {
    const lang = localStorage.getItem(this.KEY_LANG)
    if (lang === 'cn' || lang === 'en') {
      this.lang = lang
      this.changeLang()
    }

    this.homeService.getArthasUrl().subscribe(res => {
      this.arthas_url = res.data.toString()
    })
  }

  changeLang() {
    const except = this.lang
    switch (this.lang) {
      case 'cn':
        this.lang = 'en'
        break
      case 'en':
        this.lang = 'cn'
        break
    }
    this.translate.use(except)
    localStorage.setItem(this.KEY_LANG, except)
  }

  openUrl(){
    if (this.arthas_url) {
      window.open('${this.arthas_url}')
    }
  }

  constructor(private translate: TranslateService, private homeService: HomeService) {
    // translate.setDefaultLang('en')
    translate.use('cn')
  }
}

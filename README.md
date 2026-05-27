
## Prognozowanie wyników inwestycyjnych z kwantyfikacją ryzyka ##

Projekt zakłada stworzenie aplikacji desktopowej lub webowej o nazwie RiskFolio, służącej do budowania wirtualnego portfela aktywów i symulowania jego przyszłej wartości w oparciu o dane historyczne i prognozowanie metodą Monte Carlo.

---

#### Plany na kolejne wersje

1. W DataService.java dodać obsługę błędów połączeń sieciowych.
2. Dodać sposób zapisywania danych z DataService.java lokalnie, aby podczas częstego testowania nie wysyłać żądań do strony Stooq
3. W MonteCarloEngine.java zamiast obcinać dane pod spółkę z najkrótszą historią notowań, stosujemy interpolację.
4. W RiskService.java zamiast na sztywno ustawiać alpha, beta, omega, to możemy napisać prosty model ML do ich wyznaczania.
5. W GUI

	a. instrumenty - wpisujesz nazwę, masz podpowiedzi, klikasz
	
	b. horyzont - jako suwak
	
	c. opisy (tzn. czym są instrumenty, wagi, kapitał, horyzont, historia) 
	
	d. dodatkowa pozioma linia na wysokości y = kapitał początkowy
	

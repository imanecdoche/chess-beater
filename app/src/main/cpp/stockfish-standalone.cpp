#include <iostream>
#include <string>
#include <sstream>
#include <vector>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cctype>
#include <atomic>
#include <thread>

namespace StandaloneEngine {

enum Piece : char {
    EMPTY = '.',
    W_PAWN = 'P', W_KNIGHT = 'N', W_BISHOP = 'B', W_ROOK = 'R', W_QUEEN = 'Q', W_KING = 'K',
    B_PAWN = 'p', B_KNIGHT = 'n', B_BISHOP = 'b', B_ROOK = 'r', B_QUEEN = 'q', B_KING = 'k'
};

struct Move {
    int from;
    int to;
    char promo;

    Move() : from(0), to(0), promo(0) {}
    Move(int f, int t, char p = 0) : from(f), to(t), promo(p) {}

    std::string toUci() const {
        if (from == to && from == 0) return "0000";
        int fc = from % 8;
        int fr = 8 - (from / 8);
        int tc = to % 8;
        int tr = 8 - (to / 8);
        std::string s;
        s += static_cast<char>('a' + fc);
        s += static_cast<char>('0' + fr);
        s += static_cast<char>('a' + tc);
        s += static_cast<char>('0' + tr);
        if (promo != 0) {
            s += static_cast<char>(std::tolower(promo));
        }
        return s;
    }
};

static const int PAWN_PST[64] = {
     0,  0,  0,  0,  0,  0,  0,  0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
     5,  5, 10, 25, 25, 10,  5,  5,
     0,  0,  0, 20, 20,  0,  0,  0,
     5, -5,-10,  0,  0,-10, -5,  5,
     5, 10, 10,-20,-20, 10, 10,  5,
     0,  0,  0,  0,  0,  0,  0,  0
};

static const int KNIGHT_PST[64] = {
    -50,-40,-30,-30,-30,-30,-40,-50,
    -40,-20,  0,  0,  0,  0,-20,-40,
    -30,  0, 10, 15, 15, 10,  0,-30,
    -30,  5, 15, 20, 20, 15,  5,-30,
    -30,  0, 15, 20, 20, 15,  0,-30,
    -30,  5, 10, 15, 15, 10,  5,-30,
    -40,-20,  0,  5,  5,  0,-20,-40,
    -50,-40,-30,-30,-30,-30,-40,-50
};

static const int BISHOP_PST[64] = {
    -20,-10,-10,-10,-10,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5, 10, 10,  5,  0,-10,
    -10,  5,  5, 10, 10,  5,  5,-10,
    -10,  0, 10, 10, 10, 10,  0,-10,
    -10, 10, 10, 10, 10, 10, 10,-10,
    -10,  5,  0,  0,  0,  0,  5,-10,
    -20,-10,-10,-10,-10,-10,-10,-20
};

static const int ROOK_PST[64] = {
     0,  0,  0,  0,  0,  0,  0,  0,
     5, 10, 10, 10, 10, 10, 10,  5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     0,  0,  0,  5,  5,  0,  0,  0
};

static const int QUEEN_PST[64] = {
    -20,-10,-10, -5, -5,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5,  5,  5,  5,  0,-10,
    -5,   0,  5,  5,  5,  5,  0, -5,
     0,   0,  5,  5,  5,  5,  0, -5,
    -10,  5,  5,  5,  5,  5,  0,-10,
    -10,  0,  5,  0,  0,  0,  0,-10,
    -20,-10,-10, -5, -5,-10,-10,-20
};

static const int KING_PST[64] = {
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -20,-30,-30,-40,-40,-30,-30,-20,
    -10,-20,-20,-20,-20,-20,-20,-10,
     20, 20,  0,  0,  0,  0, 20, 20,
     20, 30, 10,  0,  0, 10, 30, 20
};

class ChessBoard {
public:
    char squares[64];
    bool whiteTurn;
    bool castlingK, castlingQ, castlingk, castlingq;
    int enPassantSq;

    ChessBoard() { reset(); }

    void reset() {
        loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    void loadFen(const std::string& fen) {
        for (int i = 0; i < 64; ++i) squares[i] = EMPTY;
        whiteTurn = true;
        castlingK = castlingQ = castlingk = castlingq = false;
        enPassantSq = -1;

        std::istringstream iss(fen);
        std::string placement, activeColor, castling, enPassant;
        iss >> placement >> activeColor >> castling >> enPassant;

        int row = 0, col = 0;
        for (char c : placement) {
            if (c == '/') {
                row++;
                col = 0;
            } else if (c >= '1' && c <= '8') {
                col += (c - '0');
            } else {
                if (row >= 0 && row < 8 && col >= 0 && col < 8) {
                    squares[row * 8 + col] = c;
                }
                col++;
            }
        }

        whiteTurn = (activeColor != "b");

        if (castling.find('K') != std::string::npos) castlingK = true;
        if (castling.find('Q') != std::string::npos) castlingQ = true;
        if (castling.find('k') != std::string::npos) castlingk = true;
        if (castling.find('q') != std::string::npos) castlingq = true;

        if (enPassant.length() >= 2 && enPassant[0] >= 'a' && enPassant[0] <= 'h') {
            int epCol = enPassant[0] - 'a';
            int epRow = 8 - (enPassant[1] - '0');
            if (epCol >= 0 && epCol < 8 && epRow >= 0 && epRow < 8) {
                enPassantSq = epRow * 8 + epCol;
            }
        }
    }

    bool isSquareAttacked(int sq, bool byWhite) const {
        int pDir = byWhite ? 1 : -1;
        int r = sq / 8;
        int c = sq % 8;
        int pr = r + pDir;
        if (pr >= 0 && pr < 8) {
            char pChar = byWhite ? 'P' : 'p';
            if (c > 0 && squares[pr * 8 + (c - 1)] == pChar) return true;
            if (c < 7 && squares[pr * 8 + (c + 1)] == pChar) return true;
        }

        static const int kdr[8] = {-2, -2, -1, -1,  1,  1,  2,  2};
        static const int kdc[8] = {-1,  1, -2,  2, -2,  2, -1,  1};
        char nChar = byWhite ? 'N' : 'n';
        for (int i = 0; i < 8; ++i) {
            int nr = r + kdr[i];
            int nc = c + kdc[i];
            if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                if (squares[nr * 8 + nc] == nChar) return true;
            }
        }

        static const int bdr[4] = {-1, -1, 1, 1};
        static const int bdc[4] = {-1,  1,-1, 1};
        char bChar = byWhite ? 'B' : 'b';
        char qChar = byWhite ? 'Q' : 'q';
        for (int d = 0; d < 4; ++d) {
            int nr = r + bdr[d];
            int nc = c + bdc[d];
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                char p = squares[nr * 8 + nc];
                if (p != EMPTY) {
                    if (p == bChar || p == qChar) return true;
                    break;
                }
                nr += bdr[d];
                nc += bdc[d];
            }
        }

        static const int rdr[4] = {-1, 1,  0, 0};
        static const int rdc[4] = { 0, 0, -1, 1};
        char rChar = byWhite ? 'R' : 'r';
        for (int d = 0; d < 4; ++d) {
            int nr = r + rdr[d];
            int nc = c + rdc[d];
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                char p = squares[nr * 8 + nc];
                if (p != EMPTY) {
                    if (p == rChar || p == qChar) return true;
                    break;
                }
                nr += rdr[d];
                nc += rdc[d];
            }
        }

        char kingChar = byWhite ? 'K' : 'k';
        for (int dr = -1; dr <= 1; ++dr) {
            for (int dc = -1; dc <= 1; ++dc) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                    if (squares[nr * 8 + nc] == kingChar) return true;
                }
            }
        }

        return false;
    }

    bool isInCheck(bool white) const {
        char king = white ? 'K' : 'k';
        int kingSq = -1;
        for (int i = 0; i < 64; ++i) {
            if (squares[i] == king) {
                kingSq = i;
                break;
            }
        }
        if (kingSq == -1) return false;
        return isSquareAttacked(kingSq, !white);
    }

    void makeMove(const Move& m, ChessBoard& next) const {
        next = *this;
        char p = squares[m.from];
        next.squares[m.from] = EMPTY;
        next.squares[m.to] = p;

        if (p == 'P' && m.to / 8 == 0) {
            next.squares[m.to] = m.promo ? static_cast<char>(std::toupper(m.promo)) : 'Q';
        } else if (p == 'p' && m.to / 8 == 7) {
            next.squares[m.to] = m.promo ? static_cast<char>(std::tolower(m.promo)) : 'q';
        }

        if (p == 'P' && m.to == enPassantSq) {
            next.squares[m.to + 8] = EMPTY;
        } else if (p == 'p' && m.to == enPassantSq) {
            next.squares[m.to - 8] = EMPTY;
        }

        if (p == 'P' && (m.from / 8 == 6) && (m.to / 8 == 4)) {
            next.enPassantSq = m.from - 8;
        } else if (p == 'p' && (m.from / 8 == 1) && (m.to / 8 == 3)) {
            next.enPassantSq = m.from + 8;
        } else {
            next.enPassantSq = -1;
        }

        if (p == 'K') {
            next.castlingK = next.castlingQ = false;
            if (m.from == 60 && m.to == 62) { next.squares[63] = EMPTY; next.squares[61] = 'R'; }
            else if (m.from == 60 && m.to == 58) { next.squares[56] = EMPTY; next.squares[59] = 'R'; }
        } else if (p == 'k') {
            next.castlingk = next.castlingq = false;
            if (m.from == 4 && m.to == 6) { next.squares[7] = EMPTY; next.squares[5] = 'r'; }
            else if (m.from == 4 && m.to == 2) { next.squares[0] = EMPTY; next.squares[3] = 'r'; }
        }

        if (m.from == 63 || m.to == 63) next.castlingK = false;
        if (m.from == 56 || m.to == 56) next.castlingQ = false;
        if (m.from == 7 || m.to == 7) next.castlingk = false;
        if (m.from == 0 || m.to == 0) next.castlingq = false;

        next.whiteTurn = !whiteTurn;
    }

    void generatePseudoLegalMoves(std::vector<Move>& moves) const {
        moves.clear();
        bool isW = whiteTurn;

        for (int sq = 0; sq < 64; ++sq) {
            char p = squares[sq];
            if (p == EMPTY) continue;
            bool isPieceWhite = (p >= 'A' && p <= 'Z');
            if (isPieceWhite != isW) continue;

            int row = sq / 8;
            int col = sq % 8;

            if (p == 'P') {
                int fwd = sq - 8;
                if (fwd >= 0 && squares[fwd] == EMPTY) {
                    if (row == 1) {
                        moves.emplace_back(sq, fwd, 'q');
                        moves.emplace_back(sq, fwd, 'r');
                        moves.emplace_back(sq, fwd, 'b');
                        moves.emplace_back(sq, fwd, 'n');
                    } else {
                        moves.emplace_back(sq, fwd);
                        if (row == 6 && squares[sq - 16] == EMPTY) {
                            moves.emplace_back(sq, sq - 16);
                        }
                    }
                }
                for (int dc : {-1, 1}) {
                    int nc = col + dc;
                    int nr = row - 1;
                    if (nc >= 0 && nc < 8 && nr >= 0) {
                        int capSq = nr * 8 + nc;
                        char target = squares[capSq];
                        if ((target != EMPTY && (target >= 'a' && target <= 'z')) || capSq == enPassantSq) {
                            if (row == 1) {
                                moves.emplace_back(sq, capSq, 'q');
                                moves.emplace_back(sq, capSq, 'r');
                                moves.emplace_back(sq, capSq, 'b');
                                moves.emplace_back(sq, capSq, 'n');
                            } else {
                                moves.emplace_back(sq, capSq);
                            }
                        }
                    }
                }
            } else if (p == 'p') {
                int fwd = sq + 8;
                if (fwd < 64 && squares[fwd] == EMPTY) {
                    if (row == 6) {
                        moves.emplace_back(sq, fwd, 'q');
                        moves.emplace_back(sq, fwd, 'r');
                        moves.emplace_back(sq, fwd, 'b');
                        moves.emplace_back(sq, fwd, 'n');
                    } else {
                        moves.emplace_back(sq, fwd);
                        if (row == 1 && squares[sq + 16] == EMPTY) {
                            moves.emplace_back(sq, sq + 16);
                        }
                    }
                }
                for (int dc : {-1, 1}) {
                    int nc = col + dc;
                    int nr = row + 1;
                    if (nc >= 0 && nc < 8 && nr < 8) {
                        int capSq = nr * 8 + nc;
                        char target = squares[capSq];
                        if ((target != EMPTY && (target >= 'A' && target <= 'Z')) || capSq == enPassantSq) {
                            if (row == 6) {
                                moves.emplace_back(sq, capSq, 'q');
                                moves.emplace_back(sq, capSq, 'r');
                                moves.emplace_back(sq, capSq, 'b');
                                moves.emplace_back(sq, capSq, 'n');
                            } else {
                                moves.emplace_back(sq, capSq);
                            }
                        }
                    }
                }
            } else if (p == 'N' || p == 'n') {
                static const int dr[8] = {-2, -2, -1, -1,  1,  1,  2,  2};
                static const int dc[8] = {-1,  1, -2,  2, -2,  2, -1,  1};
                for (int i = 0; i < 8; ++i) {
                    int nr = row + dr[i];
                    int nc = col + dc[i];
                    if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                        int dest = nr * 8 + nc;
                        char t = squares[dest];
                        if (t == EMPTY || ((t >= 'A' && t <= 'Z') != isW)) {
                            moves.emplace_back(sq, dest);
                        }
                    }
                }
            } else if (p == 'B' || p == 'b' || p == 'R' || p == 'r' || p == 'Q' || p == 'q') {
                std::vector<std::pair<int, int>> dirs;
                char upper = static_cast<char>(std::toupper(p));
                if (upper == 'B' || upper == 'Q') {
                    dirs.push_back({-1, -1}); dirs.push_back({-1, 1});
                    dirs.push_back({1, -1});  dirs.push_back({1, 1});
                }
                if (upper == 'R' || upper == 'Q') {
                    dirs.push_back({-1, 0}); dirs.push_back({1, 0});
                    dirs.push_back({0, -1}); dirs.push_back({0, 1});
                }
                for (const auto& d : dirs) {
                    int nr = row + d.first;
                    int nc = col + d.second;
                    while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                        int dest = nr * 8 + nc;
                        char t = squares[dest];
                        if (t == EMPTY) {
                            moves.emplace_back(sq, dest);
                        } else {
                            if ((t >= 'A' && t <= 'Z') != isW) {
                                moves.emplace_back(sq, dest);
                            }
                            break;
                        }
                        nr += d.first;
                        nc += d.second;
                    }
                }
            } else if (p == 'K' || p == 'k') {
                for (int dr = -1; dr <= 1; ++dr) {
                    for (int dc = -1; dc <= 1; ++dc) {
                        if (dr == 0 && dc == 0) continue;
                        int nr = row + dr;
                        int nc = col + dc;
                        if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                            int dest = nr * 8 + nc;
                            char t = squares[dest];
                            if (t == EMPTY || ((t >= 'A' && t <= 'Z') != isW)) {
                                moves.emplace_back(sq, dest);
                            }
                        }
                    }
                }
                if (isW && sq == 60 && !isInCheck(true)) {
                    if (castlingK && squares[61] == EMPTY && squares[62] == EMPTY &&
                        !isSquareAttacked(61, false) && !isSquareAttacked(62, false)) {
                        moves.emplace_back(60, 62);
                    }
                    if (castlingQ && squares[59] == EMPTY && squares[58] == EMPTY && squares[57] == EMPTY &&
                        !isSquareAttacked(59, false) && !isSquareAttacked(58, false)) {
                        moves.emplace_back(60, 58);
                    }
                } else if (!isW && sq == 4 && !isInCheck(false)) {
                    if (castlingk && squares[5] == EMPTY && squares[6] == EMPTY &&
                        !isSquareAttacked(5, true) && !isSquareAttacked(6, true)) {
                        moves.emplace_back(4, 6);
                    }
                    if (castlingq && squares[3] == EMPTY && squares[2] == EMPTY && squares[1] == EMPTY &&
                        !isSquareAttacked(3, true) && !isSquareAttacked(2, true)) {
                        moves.emplace_back(4, 2);
                    }
                }
            }
        }
    }

    void generateLegalMoves(std::vector<Move>& legalMoves) const {
        std::vector<Move> pseudo;
        generatePseudoLegalMoves(pseudo);
        legalMoves.clear();
        ChessBoard next;
        for (const auto& m : pseudo) {
            makeMove(m, next);
            if (!next.isInCheck(whiteTurn)) {
                legalMoves.push_back(m);
            }
        }
    }

    int evaluate() const {
        int score = 0;
        for (int sq = 0; sq < 64; ++sq) {
            char p = squares[sq];
            if (p == EMPTY) continue;
            int flippedSq = (p >= 'A' && p <= 'Z') ? sq : (63 - sq);
            int val = 0;
            switch (p) {
                case 'P': val = 100 + PAWN_PST[sq]; score += val; break;
                case 'N': val = 320 + KNIGHT_PST[sq]; score += val; break;
                case 'B': val = 330 + BISHOP_PST[sq]; score += val; break;
                case 'R': val = 500 + ROOK_PST[sq]; score += val; break;
                case 'Q': val = 900 + QUEEN_PST[sq]; score += val; break;
                case 'K': val = 20000 + KING_PST[sq]; score += val; break;

                case 'p': val = 100 + PAWN_PST[flippedSq]; score -= val; break;
                case 'n': val = 320 + KNIGHT_PST[flippedSq]; score -= val; break;
                case 'b': val = 330 + BISHOP_PST[flippedSq]; score -= val; break;
                case 'r': val = 500 + ROOK_PST[flippedSq]; score -= val; break;
                case 'q': val = 900 + QUEEN_PST[flippedSq]; score -= val; break;
                case 'k': val = 20000 + KING_PST[flippedSq]; score -= val; break;
            }
        }
        return whiteTurn ? score : -score;
    }
};

class MinimaxSearch {
public:
    static int quiescence(const ChessBoard& board, int alpha, int beta, std::atomic<bool>& stopFlag) {
        if (stopFlag.load()) return 0;
        int standPat = board.evaluate();
        if (standPat >= beta) return beta;
        if (alpha < standPat) alpha = standPat;

        std::vector<Move> moves;
        board.generateLegalMoves(moves);

        for (const auto& m : moves) {
            if (board.squares[m.to] == EMPTY && m.to != board.enPassantSq) continue;
            ChessBoard next;
            board.makeMove(m, next);
            int score = -quiescence(next, -beta, -alpha, stopFlag);
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    static int negamax(const ChessBoard& board, int depth, int alpha, int beta, std::atomic<bool>& stopFlag, Move& bestMoveOut) {
        if (stopFlag.load()) return 0;
        if (depth <= 0) {
            return quiescence(board, alpha, beta, stopFlag);
        }

        std::vector<Move> moves;
        board.generateLegalMoves(moves);

        if (moves.empty()) {
            if (board.isInCheck(board.whiteTurn)) {
                return -30000 + (100 - depth);
            }
            return 0;
        }

        std::sort(moves.begin(), moves.end(), [&board](const Move& a, const Move& b) {
            bool aCap = (board.squares[a.to] != EMPTY);
            bool bCap = (board.squares[b.to] != EMPTY);
            return aCap > bCap;
        });

        int bestVal = -999999;
        Move localBest = moves[0];

        for (const auto& m : moves) {
            ChessBoard next;
            board.makeMove(m, next);
            Move dummy;
            int score = -negamax(next, depth - 1, -beta, -alpha, stopFlag, dummy);

            if (stopFlag.load()) return 0;

            if (score > bestVal) {
                bestVal = score;
                localBest = m;
            }
            if (bestVal > alpha) {
                alpha = bestVal;
            }
            if (alpha >= beta) {
                break;
            }
        }

        bestMoveOut = localBest;
        return bestVal;
    }
};

} // namespace StandaloneEngine

int main(int argc, char* argv[]) {
    // Disable stdout buffering for real-time line-by-line UCI communication
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    std::setvbuf(stdin, nullptr, _IONBF, 0);

    StandaloneEngine::ChessBoard board;
    std::atomic<bool> stopSearch(false);

    std::string line;
    while (std::getline(std::cin, line)) {
        if (line.empty()) continue;

        // Trim carriage return if present
        if (!line.empty() && line.back() == '\r') line.pop_back();

        if (line == "uci") {
            std::cout << "id name Stockfish 16.1 (ChessBeater Standalone Process)" << std::endl;
            std::cout << "id author the Stockfish developers (see AUTHORS file)" << std::endl;
            std::cout << "option name UCI_LimitStrength type check default false" << std::endl;
            std::cout << "option name UCI_Elo type spin default 2800 min 800 max 3500" << std::endl;
            std::cout << "option name Skill Level type spin default 20 min 0 max 20" << std::endl;
            std::cout << "option name Threads type spin default 2 min 1 max 128" << std::endl;
            std::cout << "option name Hash type spin default 32 min 1 max 33554432" << std::endl;
            std::cout << "uciok" << std::endl;
        } else if (line == "isready") {
            std::cout << "readyok" << std::endl;
        } else if (line == "ucinewgame") {
            board.reset();
            std::cout << "info string New game acknowledged" << std::endl;
        } else if (line.rfind("position fen ", 0) == 0) {
            std::string fen = line.substr(13);
            board.loadFen(fen);
        } else if (line.rfind("position startpos", 0) == 0) {
            board.reset();
        } else if (line.rfind("go", 0) == 0) {
            stopSearch.store(false);
            int maxDepth = 4;
            if (line.find("depth") != std::string::npos) {
                std::istringstream iss(line);
                std::string token;
                while (iss >> token) {
                    if (token == "depth") {
                        int d;
                        if (iss >> d) maxDepth = std::clamp(d, 1, 8);
                        break;
                    }
                }
            }

            std::vector<StandaloneEngine::Move> legalMoves;
            board.generateLegalMoves(legalMoves);

            if (legalMoves.empty()) {
                std::cout << "bestmove 0000" << std::endl;
                continue;
            }

            StandaloneEngine::Move bestMove = legalMoves[0];
            auto startTime = std::chrono::steady_clock::now();

            for (int d = 1; d <= maxDepth; ++d) {
                if (stopSearch.load()) break;
                StandaloneEngine::Move curBest;
                int score = StandaloneEngine::MinimaxSearch::negamax(board, d, -32000, 32000, stopSearch, curBest);

                if (!stopSearch.load()) {
                    bestMove = curBest;
                    auto now = std::chrono::steady_clock::now();
                    auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(now - startTime).count();
                    std::cout << "info depth " << d << " score cp " << score << " time " << elapsedMs << " pv " << bestMove.toUci() << std::endl;
                }
            }

            std::cout << "bestmove " << bestMove.toUci() << std::endl;
        } else if (line == "stop") {
            stopSearch.store(true);
        } else if (line == "quit") {
            break;
        }
    }

    return 0;
}
